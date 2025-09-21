package com.bloxbean.cardano.statetrees.smt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

import java.io.ByteArrayOutputStream;

/**
 * SMT internal node with two children (left/right).
 *
 * <p>Internal nodes provide binary branching in the SMT structure. Each internal
 * node contains references to exactly two children, which can be:</p>
 * <ul>
 *   <li>Hash references to other internal nodes</li>
 *   <li>Hash references to leaf nodes</li>
 *   <li>Null references representing empty subtrees</li>
 * </ul>
 *
 * <p>When a child reference is null, it represents an empty subtree that
 * would hash to the appropriate empty commitment for that tree depth.</p>
 *
 * @since 0.8.0
 */
final class SmtInternalNode extends SmtNode {
    private final byte[] left;  // nullable child hash
    private final byte[] right; // nullable child hash

    /**
     * Private constructor for creating internal nodes.
     *
     * @param left the left child hash (nullable)
     * @param right the right child hash (nullable)
     */
    private SmtInternalNode(byte[] left, byte[] right) {
        this.left = left == null ? null : left.clone();
        this.right = right == null ? null : right.clone();
    }

    /**
     * Creates a new SMT internal node with the given child hashes.
     *
     * @param left the left child hash (can be null for empty subtree)
     * @param right the right child hash (can be null for empty subtree)
     * @return a new SmtInternalNode instance
     */
    public static SmtInternalNode of(byte[] left, byte[] right) {
        return new SmtInternalNode(left, right);
    }

    /**
     * Returns a copy of the left child hash.
     *
     * @return the left child hash (defensive copy, can be null)
     */
    public byte[] getLeft() { 
        return left == null ? null : left.clone(); 
    }

    /**
     * Returns a copy of the right child hash.
     *
     * @return the right child hash (defensive copy, can be null)
     */
    public byte[] getRight() { 
        return right == null ? null : right.clone(); 
    }

    /**
     * Creates a new internal node with updated left child.
     *
     * @param newLeft the new left child hash
     * @return a new SmtInternalNode with updated left child
     */
    public SmtInternalNode withLeft(byte[] newLeft) { 
        return new SmtInternalNode(newLeft, this.right); 
    }

    /**
     * Creates a new internal node with updated right child.
     *
     * @param newRight the new right child hash
     * @return a new SmtInternalNode with updated right child
     */
    public SmtInternalNode withRight(byte[] newRight) { 
        return new SmtInternalNode(this.left, newRight); 
    }

  @Override
  byte[] hash() {
    return Blake2b256.digest(encode());
  }

  @Override
  byte[] encode() {
    try {
      Array arr = new Array();
      arr.add(new ByteString(new byte[] { 0 })); // tag for internal node
      arr.add(new ByteString(left == null ? new byte[0] : left));
      arr.add(new ByteString(right == null ? new byte[0] : right));
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      new CborEncoder(baos).encode(arr);
      return baos.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Failed to encode SmtInternalNode", e);
    }
  }

  @Override
  public <T> T accept(SmtNodeVisitor<T> visitor) {
    return visitor.visitInternal(this);
  }

    /**
     * Decodes a CBOR array into a SmtInternalNode.
     *
     * @param cborArray the CBOR array containing [tag=0, left, right]
     * @return the decoded internal node
     */
    static SmtInternalNode decode(Array cborArray) {
        // cborArray: [ tag=0, left, right ]
        byte[] left = ((ByteString) cborArray.getDataItems().get(1)).getBytes();
        byte[] right = ((ByteString) cborArray.getDataItems().get(2)).getBytes();
        return new SmtInternalNode(left.length == 0 ? null : left, right.length == 0 ? null : right);
    }
}
