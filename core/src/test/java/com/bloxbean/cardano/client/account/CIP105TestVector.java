package com.bloxbean.cardano.client.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//https://github.com/cardano-foundation/CIPs/blob/master/CIP-0105/test-vectors/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CIP105TestVector {
    private String name;
    private String mnemonic;
    private int account;

    private String drepSigningKey;
    private String drepSigningKeyBech32;
    private String drepVerificationKey;
    private String drepVerificationKeyBech32;
    private String drepExtendedSigningKey;
    private String drepExtendedSigningKeyBech32;
    private String drepExtendedVerificationKey;
    private String drepExtendedVerificationKeyBech32;
    private String legacyDRepId;
    private String drepVerificationKeyHash;
    private String drepVkhBech32;
    private String drepScriptHash1;
    private String drepScriptHash1Bech32;
    private String drepScriptHash2;
    private String drepScriptHash2Bech32;

    private String committeeColdSigningKey;
    private String committeeColdSigningKeyBech32;
    private String committeeColdVerificationKey;
    private String committeeColdVerificationKeyBech32;
    private String committeeColdExtendedSigningKey;
    private String committeeColdExtendedSigningKeyBech32;
    private String committeeColdExtendedVerificationKey;
    private String committeeColdExtendedVerificationKeyBech32;
    private String committeeColdVerificationKeyHash;
    private String legacyCommitteeColdId;
    private String committeeColdVkhBech32;
    private String coldScriptHash1;
    private String coldScriptHash1Bech32;
    private String coldScriptHash2;
    private String coldScriptHash2Bech32;


    private String committeeHotSigningKey;
    private String committeeHotSigningKeyBech32;
    private String committeeHotVerificationKey;
    private String committeeHotVerificationKeyBech32;
    private String committeeHotExtendedSigningKey;
    private String committeeHotExtendedSigningKeyBech32;
    private String committeeHotExtendedVerificationKey;
    private String committeeHotExtendedVerificationKeyBech32;
    private String committeeHotVerificationKeyHash;
    private String legacyCommitteeHotId;
    private String committeeHotVkhBech32;
    private String ccHotScriptHash1;
    private String ccHotScriptHash1Bech32;
    private String ccHotScriptHash2;
    private String ccHotScriptHash2Bech32;

    public String toString() {
        return name;
    }

    public static CIP105TestVector testVector1() {
        return CIP105TestVector.builder()
                .name("Test Vector 1")
                .mnemonic("test walk nut penalty hip pave soap entry language right filter choice")
                .account(0)
                .drepSigningKey("a8e57a8e0a68b7ab50c6cd13e8e0811718f506d34fca674e12740fdf73e1a45e612fa30b7e4bbe9883958dcf365de1e6c1607c33172c5d3d7754f3294e450925")
                .drepSigningKeyBech32("drep_sk14rjh4rs2dzm6k5xxe5f73cypzuv02pknfl9xwnsjws8a7ulp530xztarpdlyh05csw2cmnekths7dstq0se3wtza84m4fueffezsjfglsqmad")
                .drepVerificationKey("f74d7ac30513ac1825715fd0196769761fca6e7f69de33d04ef09a0c417a752b")
                .drepVerificationKeyBech32("drep_vk17axh4sc9zwkpsft3tlgpjemfwc0u5mnld80r85zw7zdqcst6w54sdv4a4e")
                .drepExtendedSigningKey("a8e57a8e0a68b7ab50c6cd13e8e0811718f506d34fca674e12740fdf73e1a45e612fa30b7e4bbe9883958dcf365de1e6c1607c33172c5d3d7754f3294e4509251d8411029969123371cde99fb075730f1da4fd41ee7acefba7e211f0e20c91ca")
                .drepExtendedSigningKeyBech32("drep_xsk14rjh4rs2dzm6k5xxe5f73cypzuv02pknfl9xwnsjws8a7ulp530xztarpdlyh05csw2cmnekths7dstq0se3wtza84m4fueffezsjfgassgs9xtfzgehrn0fn7c82uc0rkj06s0w0t80hflzz8cwyry3eg9066uj")
                .drepExtendedVerificationKey("f74d7ac30513ac1825715fd0196769761fca6e7f69de33d04ef09a0c417a752b1d8411029969123371cde99fb075730f1da4fd41ee7acefba7e211f0e20c91ca")
                .drepExtendedVerificationKeyBech32("drep_xvk17axh4sc9zwkpsft3tlgpjemfwc0u5mnld80r85zw7zdqcst6w543mpq3q2vkjy3nw8x7n8asw4es78dyl4q7u7kwlwn7yy0sugxfrjs6z25qe")
                .drepVerificationKeyHash("a5b45515a3ff8cb7c02ce351834da324eb6dfc41b5779cb5e6b832aa")
                .drepVkhBech32("drep_vkh15k6929drl7xt0spvudgcxndryn4kmlzpk4meed0xhqe254czjh2")
                .legacyDRepId("drep15k6929drl7xt0spvudgcxndryn4kmlzpk4meed0xhqe25nle07s")
                .drepScriptHash1("d0657126dbf0c135a7224d91ca068f5bf769af6d1f1df0bce5170ec5")
                .drepScriptHash1Bech32("drep_script16pjhzfkm7rqntfezfkgu5p50t0mkntmdruwlp089zu8v29l95rg")
                .drepScriptHash2("ae5acf0511255d647c84b3184a2d522bf5f6c5b76b989f49bd383bdd")
                .drepScriptHash2Bech32("drep_script14edv7pg3y4wkglyykvvy5t2j906ld3dhdwvf7jda8qaa63d5kf4")
                .committeeColdSigningKey("684f5b480507755f387e7e544cb44b3e55eb3b88b9f6976bd41e5f746ce1a45e28b4aa8bf129088417c0fade65a98a056cbcda96c0a8874cfcbef0bf53932a12")
                .committeeColdSigningKeyBech32("cc_cold_sk1dp84kjq9qa647wr70e2yedzt8e27kwugh8mfw675re0hgm8p530z3d9230cjjzyyzlq04hn94x9q2m9um2tvp2y8fn7tau9l2wfj5yslmdl88")
                .committeeColdVerificationKey("a9781abfc1604a18ebff6fc35062c000a7a66fdca1323710ed38c1dfc3315bea")
                .committeeColdVerificationKeyBech32("cc_cold_vk149up407pvp9p36lldlp4qckqqzn6vm7u5yerwy8d8rqalse3t04q7qsvwl")
                .committeeColdExtendedSigningKey("684f5b480507755f387e7e544cb44b3e55eb3b88b9f6976bd41e5f746ce1a45e28b4aa8bf129088417c0fade65a98a056cbcda96c0a8874cfcbef0bf53932a12c601968e75ff3052ffa675aedaaea49ff36cb23036df105e28e1d32b4527e6cf")
                .committeeColdExtendedSigningKeyBech32("cc_cold_xsk1dp84kjq9qa647wr70e2yedzt8e27kwugh8mfw675re0hgm8p530z3d9230cjjzyyzlq04hn94x9q2m9um2tvp2y8fn7tau9l2wfj5ykxqxtgua0lxpf0lfn44md2afyl7dktyvpkmug9u28p6v452flxeuca0v7w")
                .committeeColdExtendedVerificationKey("a9781abfc1604a18ebff6fc35062c000a7a66fdca1323710ed38c1dfc3315beac601968e75ff3052ffa675aedaaea49ff36cb23036df105e28e1d32b4527e6cf")
                .committeeColdExtendedVerificationKeyBech32("cc_cold_xvk149up407pvp9p36lldlp4qckqqzn6vm7u5yerwy8d8rqalse3t04vvqvk3e6l7vzjl7n8ttk646jflumvkgcrdhcstc5wr5etg5n7dnc8nqv5d")
                .committeeColdVerificationKeyHash("fefb9596ed670ad2c9978d78fe4eb36ba24cbba0a62fa4cdd0c2dcf5")
                .legacyCommitteeColdId("cc_cold1lmaet9hdvu9d9jvh34u0un4ndw3yewaq5ch6fnwsctw02xxwylj")
                .committeeColdVkhBech32("cc_cold_vkh1lmaet9hdvu9d9jvh34u0un4ndw3yewaq5ch6fnwsctw0243cw47")
                .coldScriptHash1("ae6f2a27554d5e6971ef3e933e4f0be7ed7aeb60f6f93dfb81cd6e1c")
                .coldScriptHash1Bech32("cc_cold_script14ehj5f64f40xju0086fnunctulkh46mq7munm7upe4hpcwpcatv")
                .coldScriptHash2("119c20cecfedfdba057292f76bb110afa3ab472f9c35a85daf492316")
                .coldScriptHash2Bech32("cc_cold_script1zxwzpnk0ah7m5ptjjtmkhvgs4736k3e0ns66shd0fy33vdauq3j")
                .committeeHotSigningKey("d85717921e6289606e15c1e2ee65b3bd6ec247e357889ba16178eedb74e1a45ef955aa17bd002971b05e750048b766eb6df4d855c54dd2ec7ad8850e2fe35ebe")
                .committeeHotSigningKeyBech32("cc_hot_sk1mpt30ys7v2ykqms4c83wuednh4hvy3lr27yfhgtp0rhdka8p5300j4d2z77sq2t3kp082qzgkanwkm05mp2u2nwja3ad3pgw9l34a0sdh7u7e")
                .committeeHotVerificationKey("792a7f83cab90261f72ef57ee94a65ca9b0c71c1be2c8fdd5318c3643b20b52f")
                .committeeHotVerificationKeyBech32("cc_hot_vk10y48lq72hypxraew74lwjjn9e2dscuwphckglh2nrrpkgweqk5hschnzv5")
                .committeeHotExtendedSigningKey("d85717921e6289606e15c1e2ee65b3bd6ec247e357889ba16178eedb74e1a45ef955aa17bd002971b05e750048b766eb6df4d855c54dd2ec7ad8850e2fe35ebe5487e846e9a708b27681d6835fa2dac968108b3c845e379597491e6b476aa0b2")
                .committeeHotExtendedSigningKeyBech32("cc_hot_xsk1mpt30ys7v2ykqms4c83wuednh4hvy3lr27yfhgtp0rhdka8p5300j4d2z77sq2t3kp082qzgkanwkm05mp2u2nwja3ad3pgw9l34a0j5sl5yd6d8pze8dqwksd069kkfdqggk0yytcmet96fre45w64qkgyxl0dt")
                .committeeHotExtendedVerificationKey("792a7f83cab90261f72ef57ee94a65ca9b0c71c1be2c8fdd5318c3643b20b52f5487e846e9a708b27681d6835fa2dac968108b3c845e379597491e6b476aa0b2")
                .committeeHotExtendedVerificationKeyBech32("cc_hot_xvk10y48lq72hypxraew74lwjjn9e2dscuwphckglh2nrrpkgweqk5h4fplggm56wz9jw6qadq6l5tdvj6qs3v7ggh3hjkt5j8ntga42pvs5rvh0a")
                .committeeHotVerificationKeyHash("f6d29c0f7164d37610cbf67b126a993beb24a076d0653f1fa069588f")
                .legacyCommitteeHotId("cc_hot17mffcrm3vnfhvyxt7ea3y65e804jfgrk6pjn78aqd9vg7xpq8dv")
                .committeeHotVkhBech32("cc_hot_vkh17mffcrm3vnfhvyxt7ea3y65e804jfgrk6pjn78aqd9vg7vk5akz")
                .ccHotScriptHash1("d27a4229c92ec8961b6bfd32a87380dcee4a08c77b0d6c8b33f180e8")
                .ccHotScriptHash1Bech32("cc_hot_script16fayy2wf9myfvxmtl5e2suuqmnhy5zx80vxkezen7xqwskncf40")
                .ccHotScriptHash2("62e0798c7036ff35862cf42f4e7ada06f7fb5b6465390082a691be14")
                .ccHotScriptHash2Bech32("cc_hot_script1vts8nrrsxmlntp3v7sh5u7k6qmmlkkmyv5uspq4xjxlpg6u229p")
                .build();
    }

    public static CIP105TestVector testVector2() {
        return CIP105TestVector.builder()
                .name("Test Vector 2")
                .mnemonic("test walk nut penalty hip pave soap entry language right filter choice")
                .account(256)
                .drepSigningKey("10fb8436bb02e2a4d3127860f771a9f1f9aff362f202346e3238b38a76e1a45eec82a22f492d48528c7e191f52b59489adf383db4811cbce4c6cdd8cef91c408")
                .drepSigningKeyBech32("drep_sk1zracgd4mqt32f5cj0ps0wudf78u6lumz7gprgm3j8zec5ahp530weq4z9ayj6jzj33lpj86jkk2gnt0ns0d5sywteexxehvva7gugzqjur0zk")
                .drepVerificationKey("70344fe0329bbacbb33921e945daed181bd66889333eb73f3bb10ad8e4669976")
                .drepVerificationKeyBech32("drep_vk1wq6ylcpjnwavhveey855tkhdrqdav6yfxvltw0emky9d3erxn9mqdrlerg")
                .drepExtendedSigningKey("10fb8436bb02e2a4d3127860f771a9f1f9aff362f202346e3238b38a76e1a45eec82a22f492d48528c7e191f52b59489adf383db4811cbce4c6cdd8cef91c408a523761cec4182672a9592638e7017aa82ae6c1508377f4068d000a8cef56a30")
                .drepExtendedSigningKeyBech32("drep_xsk1zracgd4mqt32f5cj0ps0wudf78u6lumz7gprgm3j8zec5ahp530weq4z9ayj6jzj33lpj86jkk2gnt0ns0d5sywteexxehvva7gugz99ydmpemzpsfnj49vjvw88q9a2s2hxc9ggxal5q6xsqz5vaat2xqsha72w")
                .drepExtendedVerificationKey("70344fe0329bbacbb33921e945daed181bd66889333eb73f3bb10ad8e4669976a523761cec4182672a9592638e7017aa82ae6c1508377f4068d000a8cef56a30")
                .drepExtendedVerificationKeyBech32("drep_xvk1wq6ylcpjnwavhveey855tkhdrqdav6yfxvltw0emky9d3erxn9m22gmkrnkyrqn8922eycuwwqt64q4wds2ssdmlgp5dqq9gem6k5vq23ph3c")
                .drepVerificationKeyHash("1ed314af7d3ff8fcd320c73eb58524d774ca38733ee00ebca81bd63a")
                .drepVkhBech32("drep_vkh1rmf3ftma8lu0e5eqculttpfy6a6v5wrn8msqa09gr0tr590rpdl")
                .legacyDRepId("drep1rmf3ftma8lu0e5eqculttpfy6a6v5wrn8msqa09gr0tr5rgcuy9")
                .drepScriptHash1("3e11f3d9b39639fbb9d59c6efec7b7c1e9dbcb104523c7a4b194c45c")
                .drepScriptHash1Bech32("drep_script18cgl8kdnjculhww4n3h0a3ahc85ahjcsg53u0f93jnz9c0339av")
                .drepScriptHash2("bba45271823634a8ba9fdb981ad76df02cd2384a4e1b43c41b2734a9")
                .drepScriptHash2Bech32("drep_script1hwj9yuvzxc623w5lmwvp44md7qkdywz2fcd583qmyu62jvjnz69")
                .committeeColdSigningKey("684261ca0130e52a66861eefa275da745fd1c3f4f83100bca49904e773e1a45e0aafece23c531b52ec7915278591087a87591cb62ee96e1f716d96c9834388f4")
                .committeeColdSigningKeyBech32("cc_cold_sk1dppxrjspxrjj5e5xrmh6yaw6w30arsl5lqcsp09ynyzwwulp530q4tlvug79xx6ja3u32fu9jyy84p6erjmza6twrackm9kfsdpc3aqr79jja")
                .committeeColdVerificationKey("cab60e3b880ba64b252b942bb645d5e58ef4d6f243542fc28ce4051170171f91")
                .committeeColdVerificationKeyBech32("cc_cold_vk1e2mquwugpwnykfftjs4mv3w4uk80f4hjgd2zls5vusz3zuqhr7gs3qg4hr")
                .committeeColdExtendedSigningKey("684261ca0130e52a66861eefa275da745fd1c3f4f83100bca49904e773e1a45e0aafece23c531b52ec7915278591087a87591cb62ee96e1f716d96c9834388f43ee1839d84124acdea81c69ee7e6e828387e51067878f30cab414ec5f2e36b42")
                .committeeColdExtendedSigningKeyBech32("cc_cold_xsk1dppxrjspxrjj5e5xrmh6yaw6w30arsl5lqcsp09ynyzwwulp530q4tlvug79xx6ja3u32fu9jyy84p6erjmza6twrackm9kfsdpc3ap7uxpempqjftx74qwxnmn7d6pg8pl9zpnc0rese26pfmzl9cmtgg8xsxvu")
                .committeeColdExtendedVerificationKey("cab60e3b880ba64b252b942bb645d5e58ef4d6f243542fc28ce4051170171f913ee1839d84124acdea81c69ee7e6e828387e51067878f30cab414ec5f2e36b42")
                .committeeColdExtendedVerificationKeyBech32("cc_cold_xvk1e2mquwugpwnykfftjs4mv3w4uk80f4hjgd2zls5vusz3zuqhr7gnacvrnkzpyjkda2qud8h8um5zswr72yr8s78npj45znk97t3kkssryhkyv")
                .committeeColdVerificationKeyHash("e93734fae718e91bbf45c86f8cd81e7f9687e6cffe4c910dd1a4c360")
                .legacyCommitteeColdId("cc_cold1aymnf7h8rr53h069ephcekq707tg0ek0lexfzrw35npkq02wke0")
                .committeeColdVkhBech32("cc_cold_vkh1aymnf7h8rr53h069ephcekq707tg0ek0lexfzrw35npkquacunr")
                .coldScriptHash1("08d78337fcf51a2a9fe93dee7d21679a3c28948cd90184155040b3e4")
                .coldScriptHash1Bech32("cc_cold_script1prtcxdlu75dz48lf8hh86gt8ng7z39yvmyqcg92sgze7g6m8dtq")
                .coldScriptHash2("2e8b77ecaa9f003978dea86515cee6b97df4dff52298e60198d5b387")
                .coldScriptHash2Bech32("cc_cold_script1969h0m92nuqrj7x74pj3tnhxh97lfhl4y2vwvqvc6kecwdshr6f")
                .committeeHotSigningKey("a05672b82125c65c811ba4e7cf2e7d8b53b01baae5699aa3d618825e78e1a45e1c8a3ab1ec59bd0e79ef3d3466fb8c6823159433266aecc4ecdf21b111af0587")
                .committeeHotSigningKeyBech32("cc_hot_sk15pt89wppyhr9eqgm5nnu7tna3dfmqxa2u45e4g7krzp9u78p530pez36k8k9n0gw08hn6drxlwxxsgc4jsejv6hvcnkd7gd3zxhstpc7gujxf")
                .committeeHotVerificationKey("783ae09be2f648b59483a9bee5cace8d68c7e6e2819bfb5a1a00fbf204bea06e")
                .committeeHotVerificationKeyBech32("cc_hot_vk10qawpxlz7eytt9yr4xlwtjkw345v0ehzsxdlkks6qralyp975phqx538xn")
                .committeeHotExtendedSigningKey("a05672b82125c65c811ba4e7cf2e7d8b53b01baae5699aa3d618825e78e1a45e1c8a3ab1ec59bd0e79ef3d3466fb8c6823159433266aecc4ecdf21b111af058731609b9d64a7103fa9ab1bcdadfdea2d2366b3be0268df7f68edc9b36f8d300e")
                .committeeHotExtendedSigningKeyBech32("cc_hot_xsk15pt89wppyhr9eqgm5nnu7tna3dfmqxa2u45e4g7krzp9u78p530pez36k8k9n0gw08hn6drxlwxxsgc4jsejv6hvcnkd7gd3zxhstpe3vzde6e98zql6n2cmekklm63dydnt80szdr0h768dexeklrfspc5lznuz")
                .committeeHotExtendedVerificationKey("783ae09be2f648b59483a9bee5cace8d68c7e6e2819bfb5a1a00fbf204bea06e31609b9d64a7103fa9ab1bcdadfdea2d2366b3be0268df7f68edc9b36f8d300e")
                .committeeHotExtendedVerificationKeyBech32("cc_hot_xvk10qawpxlz7eytt9yr4xlwtjkw345v0ehzsxdlkks6qralyp975phrzcymn4j2wypl4x43hnddlh4z6gmxkwlqy6xl0a5wmjdnd7xnqrsvak8ry")
                .committeeHotVerificationKeyHash("d1d4ebdb19689e95e097919bd8712441e89b41ec36de47bf40344f85")
                .legacyCommitteeHotId("cc_hot1682whkcedz0ftcyhjxdasufyg85fks0vxm0y006qx38c2jz0ae0")
                .committeeHotVkhBech32("cc_hot_vkh1682whkcedz0ftcyhjxdasufyg85fks0vxm0y006qx38c2c4m8zp")
                .ccHotScriptHash1("bdf295c04cac9c78a69bca06cb8f2cffbee76d739759e80ec09a0655")
                .ccHotScriptHash1Bech32("cc_hot_script1hheftszv4jw83f5megrvhrevl7lwwmtnjav7srkqngr92gna52t")
                .ccHotScriptHash2("6a0b26bbf030bb6c2c8e62b0ef77c84494d771e81517ccf1434d5e26")
                .ccHotScriptHash2Bech32("cc_hot_script1dg9jdwlsxzakctywv2cw7a7ggj2dwu0gz5tueu2rf40zvkj8dwc")
                .build();
    }

    public static CIP105TestVector testVector3() {
        return CIP105TestVector.builder()
                .name("Test Vector 3")
                .mnemonic("excess behave track soul table wear ocean cash stay nature item turtle palm soccer lunch horror start stumble month panic right must lock dress")
                .account(0)
                .drepSigningKey("f05d3d37c1e3dba82444812d45f786295345b410c2fc212ee8fc88da0a118f45993d7ba4c9eb43935dc1c3d56041e2adb74c846fb8955d438d1ef2ec2496230d")
                .drepSigningKeyBech32("drep_sk17pwn6d7pu0d6sfzysyk5taux99f5tdqsct7zzthgljyd5zs33azej0tm5ny7ksunthqu84tqg832md6vs3hm392agwx3auhvyjtzxrgwyexuy")
                .drepVerificationKey("a4a2f459fcc98e7fe0acbea096f4b1fb342cb73aa6c41f62d4d6ca1464179dd6")
                .drepVerificationKeyBech32("drep_vk15j30gk0uex88lc9vh6sfda93lv6zede65mzp7ck56m9pgeqhnhtqvs6j8t")
                .drepExtendedSigningKey("f05d3d37c1e3dba82444812d45f786295345b410c2fc212ee8fc88da0a118f45993d7ba4c9eb43935dc1c3d56041e2adb74c846fb8955d438d1ef2ec2496230d5fd61ed957d6d0b2dfd6c8e2279e3eb2d5538a7399e908ddf12d1b7bfcb4b6a8")
                .drepExtendedSigningKeyBech32("drep_xsk17pwn6d7pu0d6sfzysyk5taux99f5tdqsct7zzthgljyd5zs33azej0tm5ny7ksunthqu84tqg832md6vs3hm392agwx3auhvyjtzxr2l6c0dj47k6zedl4kgugneu04j64fc5uueayydmufdrdaled9k4qllaka6")
                .drepExtendedVerificationKey("a4a2f459fcc98e7fe0acbea096f4b1fb342cb73aa6c41f62d4d6ca1464179dd65fd61ed957d6d0b2dfd6c8e2279e3eb2d5538a7399e908ddf12d1b7bfcb4b6a8")
                .drepExtendedVerificationKeyBech32("drep_xvk15j30gk0uex88lc9vh6sfda93lv6zede65mzp7ck56m9pgeqhnht9l4s7m9tad59jmltv3c38nclt942n3feen6ggmhcj6xmmlj6td2qu4ce82")
                .drepVerificationKeyHash("33e587eb1f44e51f4307eeed7ede619008bc4d1c32c18099d6367329")
                .drepVkhBech32("drep_vkh1x0jc06clgnj37sc8amkhahnpjqytcnguxtqcpxwkxeejjnrpdfp")
                .legacyDRepId("drep1x0jc06clgnj37sc8amkhahnpjqytcnguxtqcpxwkxeejj4y6sqm")
                .drepScriptHash1("f241fd096625b515f464b2b35ddebe93a2e6e2ec2e7dcac8c8ae5a33")
                .drepScriptHash1Bech32("drep_script17fql6ztxyk63taryk2e4mh47jw3wdchv9e7u4jxg4edrx89ym9g")
                .drepScriptHash2("7802a8b9e80878cc7b17c451e8778dfeef22cb7b2c2031885b881d68")
                .drepScriptHash2Bech32("drep_script10qp23w0gppuvc7chc3g7saudlmhj9jmm9ssrrzzm3qwksv3gsq7")
                .committeeColdSigningKey("b817960c5fbaf08fb98ba0768c2cf0800d1f32524c5dc2342dd27e400d118f45dc743475378ff71be1306899035c16640c22c4f09d7cf57adf6fec8ec68a3234")
                .committeeColdSigningKeyBech32("cc_cold_sk1hqtevrzlhtcglwvt5pmgct8ssqx37vjjf3wuydpd6flyqrg33azacap5w5mclacmuycx3xgrtstxgrpzcncf6l840t0klmywc69rydqvncuyc")
                .committeeColdVerificationKey("8bb15c318356b4ba8cdb2b899fd5b9c80c427d92149b6a3bd5fb3aa36dedb997")
                .committeeColdVerificationKeyBech32("cc_cold_vk13wc4cvvr266t4rxm9wyel4deeqxyylvjzjdk5w74lva2xm0dhxtsfpa2qu")
                .committeeColdExtendedSigningKey("b817960c5fbaf08fb98ba0768c2cf0800d1f32524c5dc2342dd27e400d118f45dc743475378ff71be1306899035c16640c22c4f09d7cf57adf6fec8ec68a3234a24968bef7b0cdba5393b6e494fa9e1f9f33672940dd0fbec967efef0ac4f9ce")
                .committeeColdExtendedSigningKeyBech32("cc_cold_xsk1hqtevrzlhtcglwvt5pmgct8ssqx37vjjf3wuydpd6flyqrg33azacap5w5mclacmuycx3xgrtstxgrpzcncf6l840t0klmywc69ryd9zf95taaaseka98yakuj2048slnuekw22qm58majt8alhs438eecehquu0")
                .committeeColdExtendedVerificationKey("8bb15c318356b4ba8cdb2b899fd5b9c80c427d92149b6a3bd5fb3aa36dedb997a24968bef7b0cdba5393b6e494fa9e1f9f33672940dd0fbec967efef0ac4f9ce")
                .committeeColdExtendedVerificationKeyBech32("cc_cold_xvk13wc4cvvr266t4rxm9wyel4deeqxyylvjzjdk5w74lva2xm0dhxt6yjtghmmmpnd62wfmdey5l20pl8envu55phg0hmyk0ml0ptz0nns9cqjlk")
                .committeeColdVerificationKeyHash("f0ab03c6ebd8d1b4515a3dcda3caac0737689dc3c50c5c0dfbc791f2")
                .legacyCommitteeColdId("cc_cold17z4s83htmrgmg5268hx68j4vqumk38wrc5x9cr0mc7glyntw6cl")
                .committeeColdVkhBech32("cc_cold_vkh17z4s83htmrgmg5268hx68j4vqumk38wrc5x9cr0mc7glyqucsjn")
                .coldScriptHash1("a0bc49cfc9e0394a5ee7a3cba53063479786cf1f3c03392c6694b6fd")
                .coldScriptHash1Bech32("cc_cold_script15z7ynn7fuqu55hh850962vrrg7tcdncl8spnjtrxjjm06y3avt9")
                .coldScriptHash2("eddd105e3fcb6e60bd23474bdeb9363078f0416bc967bcede1b80194")
                .coldScriptHash2Bech32("cc_cold_script1ahw3qh3ledhxp0frga9aawfkxpu0qstte9nmem0phqqegeeg6zv")
                .committeeHotSigningKey("70bbb162eb97b7e2ee490a2387ab748d788cca9dc3c3182a03cc07e612118f4565ee4cee93fc2c230735badaaead0a6282c98095dba094476729aa2d95ef6453")
                .committeeHotSigningKeyBech32("cc_hot_sk1wzamzchtj7m79mjfpg3c02m534ugej5ac0p3s2sresr7vys33azktmjva6flctprqu6m4k4w459x9qkfsz2ahgy5ganjn23djhhkg5cmwegml")
                .committeeHotVerificationKey("5f44dd7d934ab0591f743df462535ce12f6ce68ad49069289fee4cbfbcdddb6b")
                .committeeHotVerificationKeyBech32("cc_hot_vk1tazd6lvnf2c9j8m58h6xy56uuyhkee526jgxj2ylaextl0xamd4swmuygc")
                .committeeHotExtendedSigningKey("70bbb162eb97b7e2ee490a2387ab748d788cca9dc3c3182a03cc07e612118f4565ee4cee93fc2c230735badaaead0a6282c98095dba094476729aa2d95ef645334c92fcf2646fe96132f62bfb2a4af92a811beba1bc7fd0066133e5e1ddcbe14")
                .committeeHotExtendedSigningKeyBech32("cc_hot_xsk1wzamzchtj7m79mjfpg3c02m534ugej5ac0p3s2sresr7vys33azktmjva6flctprqu6m4k4w459x9qkfsz2ahgy5ganjn23djhhkg5e5eyhu7fjxl6tpxtmzh7e2ftuj4qgmawsmcl7sqesn8e0pmh97zs3c3fqj")
                .committeeHotExtendedVerificationKey("5f44dd7d934ab0591f743df462535ce12f6ce68ad49069289fee4cbfbcdddb6b34c92fcf2646fe96132f62bfb2a4af92a811beba1bc7fd0066133e5e1ddcbe14")
                .committeeHotExtendedVerificationKeyBech32("cc_hot_xvk1tazd6lvnf2c9j8m58h6xy56uuyhkee526jgxj2ylaextl0xamd4nfjf0eunydl5kzvhk90aj5jhe92q3h6aph3laqpnpx0j7rhwtu9qe7dhsc")
                .committeeHotVerificationKeyHash("c2a74e9bca6240d947f29beb7ded9604974016da2d48e3a7c3644cc4")
                .legacyCommitteeHotId("cc_hot1c2n5ax72vfqdj3ljn04hmmvkqjt5q9k694yw8f7rv3xvgxas90x")
                .committeeHotVkhBech32("cc_hot_vkh1c2n5ax72vfqdj3ljn04hmmvkqjt5q9k694yw8f7rv3xvgv2yl5g")
                .ccHotScriptHash1("5eddfce1eb7399f516fa0a19975369a8f38819765e58543fc6dc7c96")
                .ccHotScriptHash1Bech32("cc_hot_script1tmwlec0twwvl29h6pgvew5mf4recsxtktev9g07xm37fv46mta9")
                .ccHotScriptHash2("c7bcbba29f1f6e47df350691f858ec44035a217ea5a2103cad7ab874")
                .ccHotScriptHash2Bech32("cc_hot_script1c77thg5lrahy0he4q6glsk8vgsp45gt75k3pq09d02u8g4s30yx")
                .build();
    }

    public static CIP105TestVector testVector4() {
        return CIP105TestVector.builder()
                .name("Test Vector 4")
                .mnemonic("excess behave track soul table wear ocean cash stay nature item turtle palm soccer lunch horror start stumble month panic right must lock dress")
                .account(256)
                .drepSigningKey("a8b5df4daa0506f8c2a83407c96285823d4f3000c38d9e62903fab850c118f451f7dd6304ccbd215e96b09e0c1d13dbf4426872d35f90ccaef7e6692b5198a4d")
                .drepSigningKeyBech32("drep_sk14z6a7nd2q5r03s4gxsrujc59sg757vqqcwxeuc5s874c2rq33az37lwkxpxvh5s4a94sncxp6y7m73pxsuknt7gvethhue5jk5vc5ngl9zhrx")
                .drepVerificationKey("ab5d2187f2f4419421b0457f7ac8ab0d4b4ec0802af5de21dde64f603248a381")
                .drepVerificationKeyBech32("drep_vk14dwjrplj73qeggdsg4lh4j9tp495asyq9t6augwaue8kqvjg5wqskrq5yn")
                .drepExtendedSigningKey("a8b5df4daa0506f8c2a83407c96285823d4f3000c38d9e62903fab850c118f451f7dd6304ccbd215e96b09e0c1d13dbf4426872d35f90ccaef7e6692b5198a4d571a0b4d927777b6b1c2e61d361b72ac39b5f0edf498a630665e7f1e9ffd09b7")
                .drepExtendedSigningKeyBech32("drep_xsk14z6a7nd2q5r03s4gxsrujc59sg757vqqcwxeuc5s874c2rq33az37lwkxpxvh5s4a94sncxp6y7m73pxsuknt7gvethhue5jk5vc5n2hrg95mynhw7mtrshxr5mpku4v8x6lpm05nznrqej70u0fllgfkusexdkv")
                .drepExtendedVerificationKey("ab5d2187f2f4419421b0457f7ac8ab0d4b4ec0802af5de21dde64f603248a381571a0b4d927777b6b1c2e61d361b72ac39b5f0edf498a630665e7f1e9ffd09b7")
                .drepExtendedVerificationKeyBech32("drep_xvk14dwjrplj73qeggdsg4lh4j9tp495asyq9t6augwaue8kqvjg5wq4wxstfkf8waakk8pwv8fkrde2cwd47rklfx9xxpn9ulc7nl7sndcvdjh2m")
                .drepVerificationKeyHash("c1a342f0dfb82b93ca2e6b406bacb04802f7d56a99d8f95a80a8b6c5")
                .drepVkhBech32("drep_vkh1cx359uxlhq4e8j3wddqxht9sfqp004t2n8v0jk5q4zmv2chvj7w")
                .legacyDRepId("drep1cx359uxlhq4e8j3wddqxht9sfqp004t2n8v0jk5q4zmv27sh0h5")
                .drepScriptHash1("c5875315458ec9c20a91f15d36debd43df8f1fd75cc4e118db0a6691")
                .drepScriptHash1Bech32("drep_script1ckr4x9293myuyz5379wndh4ag00c787htnzwzxxmpfnfzjzk4cq")
                .drepScriptHash2("723e4a09b4897bddf8861f963312a76df8183b6ee438bdd4157b5d6c")
                .drepScriptHash2Bech32("drep_script1wgly5zd539aam7yxr7trxy48dhupswmwusutm4q40dwkcquwecx")
                .committeeColdSigningKey("b8334b6200a1766aacb330f0bdaeef08445437af44c2127ff1b9466c10118f455e123cd02e2e5e5082c58e97ae440caf49feb9ae3b0edaa9fd3611c17f51ad17")
                .committeeColdSigningKeyBech32("cc_cold_sk1hqe5kcsq59mx4t9nxrctmth0ppz9gda0gnppyll3h9rxcyq33az4uy3u6qhzuhjsstzca9awgsx27j07hxhrkrk6487nvywp0ag669c5qtm3p")
                .committeeColdVerificationKey("fec199631209a0d2e3f5e758693e4324be9b5067767637b4f0ef7f52fd6b0aaa")
                .committeeColdVerificationKeyBech32("cc_cold_vk1lmqejccjpxsd9cl4uavxj0jryjlfk5r8wemr0d8saal49lttp24q6lw08l")
                .committeeColdExtendedSigningKey("b8334b6200a1766aacb330f0bdaeef08445437af44c2127ff1b9466c10118f455e123cd02e2e5e5082c58e97ae440caf49feb9ae3b0edaa9fd3611c17f51ad177566bf28da60f674137792214fdb4e9935d16ad52b1f321b2f2fbb77eab5a716")
                .committeeColdExtendedSigningKeyBech32("cc_cold_xsk1hqe5kcsq59mx4t9nxrctmth0ppz9gda0gnppyll3h9rxcyq33az4uy3u6qhzuhjsstzca9awgsx27j07hxhrkrk6487nvywp0ag669m4v6lj3knq7e6pxaujy98akn5exhgk44ftruepkte0hdm74dd8zceqnk2h")
                .committeeColdExtendedVerificationKey("fec199631209a0d2e3f5e758693e4324be9b5067767637b4f0ef7f52fd6b0aaa7566bf28da60f674137792214fdb4e9935d16ad52b1f321b2f2fbb77eab5a716")
                .committeeColdExtendedVerificationKeyBech32("cc_cold_xvk1lmqejccjpxsd9cl4uavxj0jryjlfk5r8wemr0d8saal49lttp2482e4l9rdxpan5zdmeyg20md8fjdw3dt2jk8ejrvhjlwmha266w9syf55nr")
                .committeeColdVerificationKeyHash("4cb32ae705fb3bba3cac9742356880c912a36a4a7cca74d4956c7f41")
                .legacyCommitteeColdId("cc_cold1fjej4ec9lvam509vjapr26yqeyf2x6j20n98f4y4d3l5zygwxt4")
                .committeeColdVkhBech32("cc_cold_vkh1fjej4ec9lvam509vjapr26yqeyf2x6j20n98f4y4d3l5zhlcvpe")
                .coldScriptHash1("07ede1a2cda4f48e9f33759e76397bfdbf71267b92e5f17dd96e94be")
                .coldScriptHash1Bech32("cc_cold_script1qlk7rgkd5n6ga8enwk08vwtmlklhzfnmjtjlzlwed62tuycmmh5")
                .coldScriptHash2("ed41b6d1b16802132c147639cef6264e4fa3b093aeba965962a73061")
                .coldScriptHash2Bech32("cc_cold_script1a4qmd5d3dqppxtq5wcuuaa3xfe868vyn46afvktz5ucxzxvflg4")
                .committeeHotSigningKey("a8c57a7d8b6dd9cde9924b2ce0fa3b1151faab5893ea64f5939236d30c118f45c409f62c3364ab28f3fe38d468a771f1d2ac8bc5df78d32d6969e1bb7ff3aff1")
                .committeeHotSigningKeyBech32("cc_hot_sk14rzh5lvtdhvum6vjfvkwp73mz9gl426cj04xfavnjgmdxrq33azugz0k9sekf2eg70lr34rg5aclr54v30za77xn945kncdm0le6lugud8v57")
                .committeeHotVerificationKey("428aaa4d7c9ed7776b5019d7e64419f27f0ad3d47078b8963ac2382b7b7a7553")
                .committeeHotVerificationKeyBech32("cc_hot_vk1g2925ntunmthw66sr8t7v3qe7fls4575wput3936cguzk7m6w4fs0zjxf8")
                .committeeHotExtendedSigningKey("a8c57a7d8b6dd9cde9924b2ce0fa3b1151faab5893ea64f5939236d30c118f45c409f62c3364ab28f3fe38d468a771f1d2ac8bc5df78d32d6969e1bb7ff3aff166f8e9d1c694e53ae02d57b6dbc1b2a066ab8d85112880aede4605b333b2da50")
                .committeeHotExtendedSigningKeyBech32("cc_hot_xsk14rzh5lvtdhvum6vjfvkwp73mz9gl426cj04xfavnjgmdxrq33azugz0k9sekf2eg70lr34rg5aclr54v30za77xn945kncdm0le6lutxlr5ar355u5awqt2hkmdurv4qv64cmpg39zq2ahjxqken8vk62qunx4hl")
                .committeeHotExtendedVerificationKey("428aaa4d7c9ed7776b5019d7e64419f27f0ad3d47078b8963ac2382b7b7a755366f8e9d1c694e53ae02d57b6dbc1b2a066ab8d85112880aede4605b333b2da50")
                .committeeHotExtendedVerificationKeyBech32("cc_hot_xvk1g2925ntunmthw66sr8t7v3qe7fls4575wput3936cguzk7m6w4fkd78f68rffef6uqk40dkmcxe2qe4t3kz3z2yq4m0yvpdnxwed55q798msd")
                .committeeHotVerificationKeyHash("a9eb44d0aa1ce5559b7c22270ac23d1e61bf2e114dd8e5a44ed3a529")
                .legacyCommitteeHotId("cc_hot14845f592rnj4txmuygns4s3aresm7ts3fhvwtfzw6wjjj3l0520")
                .committeeHotVkhBech32("cc_hot_vkh14845f592rnj4txmuygns4s3aresm7ts3fhvwtfzw6wjjjmgmw3p")
                .ccHotScriptHash1("9d55b1aab952b24807bedbc9af8283b1d798023432f484a2d9160dfe")
                .ccHotScriptHash1Bech32("cc_hot_script1n42mr24e22eyspa7m0y6lq5rk8tesq35xt6gfgkezcxluqysk4n")
                .ccHotScriptHash2("4241b3550fc0aca9895b50d3d722bbca8f197fce155c9843817c7ac5")
                .ccHotScriptHash2Bech32("cc_hot_script1gfqmx4g0czk2nz2m2rfawg4me283jl7wz4wfssup03av2yzf2kd")
                .build();
    }

}
