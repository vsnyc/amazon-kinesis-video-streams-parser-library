package com.amazonaws.kinesisvideo.parser.examples;

import com.amazonaws.kinesisvideo.parser.mkv.Frame;
import com.amazonaws.kinesisvideo.parser.utilities.FrameVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.MkvTrackMetadata;
import org.jcodec.codecs.h264.H264Decoder;
import org.jcodec.codecs.h264.mp4.AvcCBox;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.jcodec.scale.Transform;
import org.jcodec.scale.Yuv420jToRgb;
import org.zeromq.ZMQ;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.jcodec.codecs.h264.H264Utils.splitMOVPacket;

public class OpenposeFrameRenderer implements FrameVisitor.FrameProcessor {

    public static final String SRC_IMAGES_DIR = "/home/ubuntu/images/";
    public static final String JSON_DIR = "/home/ubuntu/json/";
    public static final String RENDERED_IMAGES_DIR = "/home/ubuntu/rendered/";
    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ss.SSS");
    public static final int ZEROMQ_PORT = 5555;

    @SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(com.amazonaws.kinesisvideo.parser.utilities.H264FrameRenderer.class);
    private final KinesisVideoFrameViewer kinesisVideoFrameViewer;
    private final H264Decoder decoder = new H264Decoder();
    private final Transform transform = new Yuv420jToRgb();
    private int frameCount;
    private byte[] codecPrivateData;
    private ZMQ.Context context;

    private OpenposeFrameRenderer(KinesisVideoFrameViewer kinesisVideoFrameViewer) {
        this.context = ZMQ.context(1);
        this.kinesisVideoFrameViewer = kinesisVideoFrameViewer;
        this.kinesisVideoFrameViewer.setVisible(true);
    }

    public static OpenposeFrameRenderer create(KinesisVideoFrameViewer kinesisVideoFrameViewer) {
        return new OpenposeFrameRenderer(kinesisVideoFrameViewer);
    }

    public String messageZeroMQ(String message, int port) {
        //  Socket to talk to server
        ZMQ.Socket requester = context.socket(ZMQ.REQ);
        requester.connect("tcp://localhost:" + port);
        requester.send(message.getBytes(), 0);
        String reply = new String(requester.recv());
        requester.close();
        return reply;
    }

    @Override
    public void process(Frame frame, MkvTrackMetadata trackMetadata) {
        ByteBuffer frameBuffer = frame.getFrameData();
        int pixelWidth = trackMetadata.getPixelWidth().get().intValue();
        int pixelHeight = trackMetadata.getPixelHeight().get().intValue();
        codecPrivateData = trackMetadata.getCodecPrivateData().array();
        log.debug("Decoding frames ... ");
        // Read the bytes that appear to comprise the header
        // See: https://www.matroska.org/technical/specs/index.html#simpleblock_structure
        Picture rgb = Picture.create(pixelWidth, pixelHeight, ColorSpace.RGB);
        BufferedImage renderImage = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_3BYTE_BGR);
        BufferedImage openposeImage = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_3BYTE_BGR);
        boolean openposeSuccess = false;
        AvcCBox avcC = AvcCBox.parseAvcCBox(ByteBuffer.wrap(codecPrivateData));
        decoder.addSps(avcC.getSpsList());
        decoder.addPps(avcC.getPpsList());
        Picture buf = Picture.create(pixelWidth, pixelHeight, ColorSpace.YUV420J);
        List<ByteBuffer> byteBuffers = splitMOVPacket(frameBuffer, avcC);
        Picture pic = decoder.decodeFrameFromNals(byteBuffers, buf.getData());
        if (pic != null) {
            // Work around for color issues in JCodec
            // https://github.com/jcodec/jcodec/issues/59
            // https://github.com/jcodec/jcodec/issues/192
            byte[][] dataTemp = new byte[3][pic.getData().length];
            dataTemp[0] = pic.getPlaneData(0);
            dataTemp[1] = pic.getPlaneData(2);
            dataTemp[2] = pic.getPlaneData(1);
            Picture tmpBuf = Picture.createPicture(pixelWidth, pixelHeight, dataTemp, ColorSpace.YUV420J);
            transform.transform(tmpBuf, rgb);
            AWTUtil.toBufferedImage(rgb, renderImage);

            try {
                String fileName = "image" + dateFormat.format(new Date());
                ImageIO.write(renderImage, "png", new File(SRC_IMAGES_DIR + fileName + ".png"));
                String reply = messageZeroMQ(fileName + ".png", ZEROMQ_PORT);
                System.out.println(reply);
                if (reply != null && reply.contains("Done")) {
                    openposeImage = ImageIO.read(new File(RENDERED_IMAGES_DIR + fileName + "_rendered.png"));
                    openposeSuccess = true;
                }

            } catch (IOException e) {
                log.warn("Couldn't convert to a PNG", e);
            }


            if (openposeSuccess) {
                kinesisVideoFrameViewer.update(openposeImage);
            } else {
                kinesisVideoFrameViewer.update(renderImage);
            }
            frameCount++;
        }
    }

    public ByteBuffer getCodecPrivateData() {
        return ByteBuffer.wrap(codecPrivateData);
    }

    @SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    public int getFrameCount() {
        return this.frameCount;
    }
}
