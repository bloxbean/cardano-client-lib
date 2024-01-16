package com.bloxbean.cardano.client.cip.cip68;

import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.cip.cip25.NFTFile;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of CIP-68 rich fungible token.
 * According to specification: https://developers.cardano.org/docs/governance/cardano-improvement-proposals/cip-0068/#444-rft-standard
 */
public class CIP68RFT extends CIP68TokenTemplate<CIP68FT> {

    private static final int ASSET_NAME_LABEL = 444;
    private static final String IMAGE_KEY = "image";
    private static final String DECIMALS_KEY = "decimals";
    private static final String FILES_KEY = "files";

    CIP68RFT() {
        super(ASSET_NAME_LABEL);
    }

    private CIP68RFT(Map map) {
        super(map, ASSET_NAME_LABEL);
    }

    /**
     * Create instance of CIP68 RFT
     * @return
     */
    public static CIP68RFT create() {
        return new CIP68RFT();
    }

    /**
     * Set image
     * @param image
     * @return adjusted CIP68RFT object
     */
    public CIP68RFT image(String image) {
        put(IMAGE_KEY, image);
        return this;
    }

    /**
     * get image
     * @return
     */
    public String getImage() {
        return (String) get(IMAGE_KEY);
    }

    /**
     * set decimals
     * @param decimals
     * @return adjusted CIP68RFT object
     */
    public CIP68RFT decimals(int decimals) {
        put(DECIMALS_KEY, BigInteger.valueOf(decimals));
        return this;
    }

    /**
     * get decimals
     * @return
     */
    public int getDecimals() {
        return (int) get(DECIMALS_KEY);
    }

    /**
     * Add file
     * @param nftFile
     * @return adjusted CIP68RFT object
     */
    public CIP68RFT addFile(NFTFile nftFile) {
        CBORMetadataList files = (CBORMetadataList) get(FILES_KEY);
        if(files == null) {
            files = new CBORMetadataList();
            put(FILES_KEY, files);
        }
        files.add(nftFile);

        return this;
    }

    /**
     * get all files
     * @return
     */
    public List<NFTFile> getFiles() {
        CBORMetadataList files = (CBORMetadataList) get(FILES_KEY);

        List<NFTFile> nftFiles = new ArrayList<>();
        for (DataItem di : files.getArray().getDataItems()) {
            NFTFile nftFile = NFTFile.create((Map) di);
            nftFiles.add(nftFile);
        }
        return nftFiles;
    }
}
