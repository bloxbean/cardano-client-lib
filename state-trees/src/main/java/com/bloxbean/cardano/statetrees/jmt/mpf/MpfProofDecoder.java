package com.bloxbean.cardano.statetrees.jmt.mpf;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Tag;
import co.nstant.in.cbor.model.UnsignedInteger;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

public final class MpfProofDecoder {

  private MpfProofDecoder() {
    throw new AssertionError("Utility class");
  }

  public static MpfProof decode(byte[] cborBytes) {
    try {
      List<DataItem> items = new CborDecoder(new ByteArrayInputStream(cborBytes)).decode();
      if (items.isEmpty()) {
        throw new IllegalArgumentException("Empty MPF proof");
      }
      DataItem root = items.get(0);
      if (!(root instanceof Array)) {
        throw new IllegalArgumentException("MPF proof must be an array");
      }
      Array stepsArray = (Array) root;
      List<MpfProof.Step> steps = new ArrayList<>();
      for (DataItem item : stepsArray.getDataItems()) {
        if (!(item instanceof Array)) {
          throw new IllegalArgumentException("MPF proof step must be tagged array");
        }
        Array tagged = (Array) item;
        Tag tag = tagged.getTag();
        if (tag == null) {
          throw new IllegalArgumentException("MPF proof step missing tag");
        }
        int tagValue = (int) tag.getValue();
        if (tagValue == 121) {
          steps.add(decodeBranchStep(tagged));
        } else if (tagValue == 122) {
          steps.add(decodeForkStep(tagged));
        } else if (tagValue == 123) {
          steps.add(decodeLeafStep(tagged));
        } else {
          throw new IllegalArgumentException("Unsupported MPF proof step tag: " + tagValue);
        }
      }
      return new MpfProof(steps);
    } catch (CborException e) {
      throw new IllegalArgumentException("Failed to decode MPF proof", e);
    }
  }

  private static MpfProof.BranchStep decodeBranchStep(Array array) {
    List<DataItem> dataItems = array.getDataItems();
    int skip = toInt(dataItems.get(0));
    byte[] neighborsBytes = ((ByteString) dataItems.get(1)).getBytes();
    int digestLength = neighborsBytes.length / 4;
    byte[][] neighbors = new byte[4][];
    for (int i = 0; i < 4; i++) {
      byte[] digest = new byte[digestLength];
      System.arraycopy(neighborsBytes, i * digestLength, digest, 0, digestLength);
      neighbors[i] = digest;
    }
    return new MpfProof.BranchStep(skip, neighbors);
  }

  private static MpfProof.ForkStep decodeForkStep(Array array) {
    List<DataItem> dataItems = array.getDataItems();
    int skip = toInt(dataItems.get(0));
    Array neighborArray = (Array) dataItems.get(1);
    if (neighborArray.getTag() == null || neighborArray.getTag().getValue() != 121) {
      throw new IllegalArgumentException("Fork neighbor must be tagged with 121");
    }
    int nibble = toInt(neighborArray.getDataItems().get(0));
    byte[] prefix = ((ByteString) neighborArray.getDataItems().get(1)).getBytes();
    byte[] root = ((ByteString) neighborArray.getDataItems().get(2)).getBytes();
    return new MpfProof.ForkStep(skip, nibble, prefix, root);
  }

  private static MpfProof.LeafStep decodeLeafStep(Array array) {
    List<DataItem> dataItems = array.getDataItems();
    int skip = toInt(dataItems.get(0));
    byte[] keyHash = ((ByteString) dataItems.get(1)).getBytes();
    byte[] valueHash = ((ByteString) dataItems.get(2)).getBytes();
    return new MpfProof.LeafStep(skip, keyHash, valueHash);
  }

  private static int toInt(DataItem item) {
    if (!(item instanceof UnsignedInteger)) {
      throw new IllegalArgumentException("Expected unsigned integer");
    }
    return ((UnsignedInteger) item).getValue().intValue();
  }
}
