package com.google.googlejavaformat.java;

import static com.sun.tools.javac.tree.JCTree.Tag;

/**
 * Created by Duy on 22-Jul-17.
 */

public class OpUtil {
    public static boolean isPostUnaryOp(Tag tag) {
        return tag == Tag.POSTINC || tag == Tag.POSTDEC;
    }

    public boolean isIncOrDecUnaryOp(Tag tag) {
        return (tag == Tag.PREINC || tag == Tag.PREDEC || tag == Tag.POSTINC || tag == Tag.POSTDEC);
    }

}
