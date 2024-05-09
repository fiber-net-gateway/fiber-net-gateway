package io.fiber.net.common.json.parse;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;

public class JsonParser {

    private static final int VEC = 0x01;
    private static final int IJC = 0x02;
    private static final int VHC = 0x04;
    private static final int NFP = 0x08;
    private static final int NUC = 0x10;

    private static final byte[] CHAR_LOOK_UP_TABLE = new byte[]{
/*00*/ IJC, IJC, IJC, IJC, IJC, IJC, IJC, IJC,
/*08*/ IJC, IJC, IJC, IJC, IJC, IJC, IJC, IJC,
/*10*/ IJC, IJC, IJC, IJC, IJC, IJC, IJC, IJC,
/*18*/ IJC, IJC, IJC, IJC, IJC, IJC, IJC, IJC,

/*20*/ 0, 0, NFP | VEC | IJC, 0, 0, 0, 0, 0,
/*28*/ 0, 0, 0, 0, 0, 0, 0, VEC,
/*30*/ VHC, VHC, VHC, VHC, VHC, VHC, VHC, VHC,
/*38*/ VHC, VHC, 0, 0, 0, 0, 0, 0,

/*40*/ 0, VHC, VHC, VHC, VHC, VHC, VHC, 0,
/*48*/ 0, 0, 0, 0, 0, 0, 0, 0,
/*50*/ 0, 0, 0, 0, 0, 0, 0, 0,
/*58*/ 0, 0, 0, 0, NFP | VEC | IJC, 0, 0, 0,

/*60*/ 0, VHC, VEC | VHC, VHC, VHC, VHC, VEC | VHC, 0,
/*68*/ 0, 0, 0, 0, 0, 0, VEC, 0,
/*70*/ 0, 0, VEC, 0, VEC, 0, 0, 0,
/*78*/ 0, 0, 0, 0, 0, 0, 0, 0,

            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,
            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,
            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,
            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,

            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,
            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,
            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,
            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,

            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,
            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,
            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,
            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,

            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,
            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,
            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC,
            NUC, NUC, NUC, NUC, NUC, NUC, NUC, NUC
    };


    public void feed(ByteBuf input) {

    }

    public void feed(ByteBuffer[] input) {

    }

    public void feed(ByteBuffer input) {

    }

    public void feed(byte[] input) {

    }

}
