package com.fishingtime.region.util;

/**
 * 行政区划代码解析器
 *
 * 根据 adcode 推导行政级别和上级 adcode。
 *
 * 规则：
 * - 100000      → 国家，level=0，parent=null
 * - XX0000      → 省级，level=1，parent=100000
 * - XXXX00      → 市级，level=2，parent=前两位+0000
 * - 其他        → 区县级，level=3，parent=前四位+00
 */
public final class AdcodeParser {

    /** 国家级别代码 */
    public static final String COUNTRY_ADCODE = "100000";

    private AdcodeParser() {}

    /**
     * 判断行政级别
     * 0=国家 1=省 2=市 3=区县
     */
    public static int parseLevel(String adcode) {
        if (adcode == null) return 3;
        if (COUNTRY_ADCODE.equals(adcode)) return 0;
        if (adcode.matches("\\d{2}0000")) return 1;
        if (adcode.matches("\\d{4}00")) return 2;
        return 3;
    }

    /**
     * 推导上级 adcode
     */
    public static String parseParent(String adcode) {
        int level = parseLevel(adcode);
        switch (level) {
            case 0:
                return null;
            case 1:
                return COUNTRY_ADCODE;
            case 2:
                return adcode.substring(0, 2) + "0000";
            case 3:
                return adcode.substring(0, 4) + "00";
            default:
                return null;
        }
    }
}
