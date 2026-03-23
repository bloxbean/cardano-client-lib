package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

@MetadataType
public class SampleAudioMedia implements SampleMedia {
    private String url;
    private int duration;

    public SampleAudioMedia() {}

    public SampleAudioMedia(String url, int duration) {
        this.url = url;
        this.duration = duration;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
}
