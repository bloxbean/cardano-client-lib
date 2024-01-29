package com.bloxbean.cardano.client.cip.cip68;

import com.bloxbean.cardano.client.cip.cip68.common.CIP68Datum;
import com.bloxbean.cardano.client.cip.cip68.common.CIP68File;
import com.bloxbean.cardano.client.cip.cip68.common.CIP68TokenTemplate;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.cip.cip68.common.CIP68Util.toByteString;

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

    private CIP68NFT(CIP68Datum datum) {
        super(ASSET_NAME_LABEL, datum);
    }

    /**
     * create instance of CIP68 NFT
     *
     * @return
     */
    public static CIP68NFT create() {
        return new CIP68NFT();
    }

    /**
     * set image uri
     *
     * @param imageUri
     * @return adjusted CIP68NFT object
     */
    public CIP68NFT image(String imageUri) {
        property(IMAGE_KEY, imageUri);
        return this;
    }

    /**
     * get image
     *
     * @return
     */
    public String getImage() {
        return getStringProperty(IMAGE_KEY);
    }

    /**
     * Add file
     *
     * @param file
     * @return adjusted CIP68NFT object
     */
    public CIP68NFT addFile(@NonNull CIP68File file) {
        ListPlutusData files = (ListPlutusData) getProperty(toByteString(FILES_KEY));
        if (files == null) {
            files = new ListPlutusData();
            property(toByteString(FILES_KEY), files);
        }
        files.add(file.toPlutusData());

        return this;
    }

    /**
     * get all files
     *
     * @return
     */
    @SneakyThrows
    public List<CIP68File> getFiles() {
        ListPlutusData filesList = (ListPlutusData) getProperty(toByteString(FILES_KEY));

        List<CIP68File> files = new ArrayList<>();
        for (PlutusData filePD : filesList.getPlutusDataList()) {
            CIP68File file = CIP68File.create((MapPlutusData) filePD);
            files.add(file);
        }
        return files;
    }

    public static CIP68NFT fromDatum(byte[] datumBytes) {
        CIP68NFT token = CIP68NFT.create();
        token.populateFromDatumBytes(datumBytes);
        return token;
    }
}
