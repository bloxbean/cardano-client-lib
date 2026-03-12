package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.util.List;

/**
 * POJO for testing 2+ levels of nesting (nested within nested).
 */
@MetadataType
public class SampleDeepNested {

    private String id;
    private SampleDeepInner inner;
    private List<SampleDeepInner> innerList;

    public SampleDeepNested() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public SampleDeepInner getInner() { return inner; }
    public void setInner(SampleDeepInner inner) { this.inner = inner; }

    public List<SampleDeepInner> getInnerList() { return innerList; }
    public void setInnerList(List<SampleDeepInner> innerList) { this.innerList = innerList; }
}
