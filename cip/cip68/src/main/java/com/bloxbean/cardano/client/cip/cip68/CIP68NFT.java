package com.bloxbean.cardano.client.cip.cip68;

import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.cip.cip25.NFTFile;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of CIP-68 non-fungible token
 * According to specification: https://developers.cardano.org/docs/governance/cardano-improvement-proposals/cip-0068/#222-nft-standard
 */
public class CIP68NFT extends CIP68TokenTemplate<CIP68NFT> {
    private static final int ASSET_NAME_LABEL = 222;
    private static final String IMAGE_KEY = "image";
    private static final String FILES_KEY = "files";

    CIP68NFT() {
        super(ASSET_NAME_LABEL);
    }

    private CIP68NFT(Map map) {
        super(map, ASSET_NAME_LABEL);
    }

    /**
     * create instance of CIP68 NFT
     * @return
     */
    public static CIP68NFT create() {
        return new CIP68NFT();
    }

    /**
     * set image uri
     * @param imageUri
     * @return adjusted CIP68NFT object
     */
    public CIP68NFT image(String imageUri) {
        put(IMAGE_KEY, imageUri);
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
     * add a file to nft
     * @param nftFile
     * @return adjusted CIP68NFT object
     */
    public CIP68NFT addFile(NFTFile nftFile) {
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
