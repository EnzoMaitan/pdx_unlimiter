package com.crschnick.pdx_unlimiter.app.util;

public class Hyperlinks {

    public static final String MAIN_PAGE = "https://github.com/crschnick/pdx_unlimiter/";
    public static final String GUIDE = "https://github.com/crschnick/pdx_unlimiter/wiki/User-Guide";
    public static final String EDITOR_GUIDE = "https://github.com/crschnick/pdx_unlimiter/wiki/Editor-Guide";
    public static final String DISCORD = "https://discord.com/invite/BVE4vxqFpU";
    public static final String NEW_ISSUE = "https://github.com/crschnick/pdx_unlimiter/issues/new";

    public static final String CK3_TO_EU4_DOWNLOADS = "https://github.com/ParadoxGameConverters/CK3toEU4/releases";
    public static final String RAKALY_MAIN_PAGE = "https://rakaly.com/eu4";
    public static final String EU4_SE_MAIN_PAGE = "https://forum.paradoxplaza.com/forum/threads/save-game-editor.1450703/";

    public static void open(String url) {
        ThreadHelper.browse(url);
    }
}