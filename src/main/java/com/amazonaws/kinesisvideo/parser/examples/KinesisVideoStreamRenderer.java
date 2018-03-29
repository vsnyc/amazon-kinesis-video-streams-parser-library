package com.amazonaws.kinesisvideo.parser.examples;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FrameVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.H264FrameRenderer;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMedia;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMediaClient;
import com.amazonaws.services.kinesisvideo.model.*;

import java.util.Date;
import java.util.Objects;

public class KinesisVideoStreamRenderer {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KinesisVideoStreamRenderer.class);
    private static final int FRAME_WIDTH = 640;
    private static final int FRAME_HEIGHT = 480;
    public static final String STREAM_NAME = "ddr-pi-stream";

    public static void main(String[] args) {

        AWSCredentialsProvider credentialsProvider;

        if (Objects.equals(System.getProperty("app.runMode"), "local")) {
            credentialsProvider = new ProfileCredentialsProvider();
        } else {
            credentialsProvider = InstanceProfileCredentialsProvider.getInstance();
        }

        AmazonKinesisVideo client = AmazonKinesisVideoClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion(Regions.US_WEST_2)
                .build();

        String getMediaEndpoint = client.getDataEndpoint(new GetDataEndpointRequest().withStreamName(STREAM_NAME)
                .withAPIName(APIName.GET_MEDIA)).getDataEndpoint();
        System.out.println("GetMedia endpoint " + getMediaEndpoint);

        AmazonKinesisVideoMedia mediaClient = AmazonKinesisVideoMediaClient.builder()
                .withCredentials(credentialsProvider)
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(getMediaEndpoint,
                        Regions.US_WEST_2.getName()))
                .build();

        StartSelector selectorTimestamp = new StartSelector()
                .withStartSelectorType(StartSelectorType.SERVER_TIMESTAMP)
                .withStartTimestamp(new Date());

        StartSelector selectorEarliest = new StartSelector().withStartSelectorType(StartSelectorType.EARLIEST);
        StartSelector selectorNow = new StartSelector().withStartSelectorType(StartSelectorType.NOW);

        StartSelector selector = selectorNow;

        viewMedia(mediaClient, STREAM_NAME, selector);

    }

    private static void viewMedia(AmazonKinesisVideoMedia mediaClient, String streamName, StartSelector selector) {
        viewMedia(mediaClient, streamName, selector, FRAME_WIDTH, FRAME_HEIGHT);
    }

    private static void viewMedia(AmazonKinesisVideoMedia mediaClient, String streamName, StartSelector selector, int frameWidth, int frameHeight) {
        GetMediaResult result = mediaClient.getMedia(new GetMediaRequest()
                .withStreamName(streamName)
                .withStartSelector(selector)
        );
        KinesisVideoFrameViewer kinesisVideoFrameViewer = new KinesisVideoFrameViewer(frameWidth, frameHeight);
        StreamingMkvReader mkvStreamReader = StreamingMkvReader.createDefault(new InputStreamParserByteSource(result.getPayload()));
        FrameVisitor frameVisitor = FrameVisitor.create(OpenposeFrameRenderer.create(kinesisVideoFrameViewer));
        try {
            mkvStreamReader.apply(frameVisitor);
        } catch (MkvElementVisitException e) {
            log.error("Exception while accepting visitor {}", e);
        }
    }

}
