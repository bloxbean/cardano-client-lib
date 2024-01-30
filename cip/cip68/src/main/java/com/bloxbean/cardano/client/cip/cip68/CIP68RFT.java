package com.bloxbean.cardano.client.cip.cip68;

import com.bloxbean.cardano.client.cip.cip68.common.CIP68Datum;
import com.bloxbean.cardano.client.cip.cip68.common.CIP68File;
import com.bloxbean.cardano.client.cip.cip68.common.CIP68TokenTemplate;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.cip.cip68.common.CIP68Util.toByteString;

/**
 * Implementation of CIP-68 rich fungible token.
 * According to specification: https://developers.cardano.org/docs/governance/cardano-improvement-proposals/cip-0068/#444-rft-standard
 */
public class CIP68RFT extends CIP68TokenTemplate<CIP68RFT> {

    private static final int ASSET_NAME_LABEL = 444;
    private static final String IMAGE_KEY = "image";
    private static final String DECIMALS_KEY = "decimals";
    private static final String FILES_KEY = "files";

    CIP68RFT() {
        super(ASSET_NAME_LABEL);
        getDatum().setVersion(2);
    }

    private CIP68RFT(CIP68Datum cip68Datum) {
        super(ASSET_NAME_LABEL, cip68Datum);
        getDatum().setVersion(2);
    }

    /**
     * Create instance of CIP68 RFT
     *
     * @return
     */
    public static CIP68RFT create() {
        return new CIP68RFT();
    }

    /**
     * Set image
     *
     * @param image
     * @return adjusted CIP68RFT object
     */
    public CIP68RFT image(String image) {
        property(IMAGE_KEY, image);
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
     * set decimals
     *
     * @param decimals
     * @return adjusted CIP68RFT object
     */
    public CIP68RFT decimals(int decimals) {
        property(toByteString(DECIMALS_KEY), BigIntPlutusData.of(decimals));
        return this;
    }

    /**
     * get decimals
     *
     * @return
     */
    public Integer getDecimals() {
        var decimalsPlutusData = (BigIntPlutusData) mapPlutusData.getMap().get(toByteString(DECIMALS_KEY));
        if (decimalsPlutusData == null)
            return null;
        return decimalsPlutusData.getValue().intValue();
    }

    /**
     * Add file
     *
     * @param file
     * @return adjusted CIP68RFT object
     */
    public CIP68RFT addFile(@NonNull CIP68File file) {
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


    public static CIP68RFT fromDatum(byte[] datumBytes) {
        CIP68RFT token = CIP68RFT.create();
        token.populateFromDatumBytes(datumBytes);
        return token;
    }
}
