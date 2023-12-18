package io.fiber.net.script;

public interface Code {

    int NOOP = 1;
    int SWAP = 2;
    int LOAD_CONST = 3;
    int LOAD_ROOT = 4;
    int DUMP = 5;
    int POP = 6;
    int LOAD_VAR = 7;
    int STORE_VAR = 8;
    int STORE_VAR_1 = 9;

    //
    int BOP_PLUS = 20;
    int BOP_MINUS = 21;
    int BOP_MULTIPLY = 22;
    int BOP_DIVIDE = 23;
    int BOP_MOD = 24;
    int BOP_MATCH = 25; //~

    int BOP_LT = 26; //<
    int BOP_LTE = 27; //<=
    int BOP_GT = 28; //~
    int BOP_GTE = 29; //~
    int BOP_EQ = 30; //~
    int BOP_SEQ = 31; //~
    int BOP_NE = 32; //~
    int BOP_SNE = 33; //~


    int BOP_IN = 34; // in


    //
    int UNARY_PLUS = 40;
    int UNARY_MINUS = 41;
    int UNARY_NEG = 42;//!
    int UNARY_TYPEOF = 43;//!

    //
    int NEW_OBJECT = 50;
    int NEW_ARRAY = 51;
    int EXP_OBJECT = 52;
    int EXP_ARRAY = 53;
    int PUSH_ARRAY = 54;
    //
    int IDX_SET = 60;//!
    int IDX_GET = 61;//!
    int PROP_GET = 62;//!
    int PROP_SET = 63;//! {a:1}
    int PROP_SET_1 = 64;//! {a:1}
    //
    int CALL_FUNC = 72;
    int CALL_FUNC_SPREAD = 73;
    int CALL_CONST = 74;
    //
    int JUMP = 80;
    int JUMP_IF_FALSE = 81;
    int JUMP_IF_TRUE = 82;
    int LOGICAL_AND = 83;
    int LOGICAL_OR = 84;


    int INTO_ITERATE = 90;
    int NEXT_ITERATE = 91;
    int END_ITERATE = 92;

    int INTO_TRY = 100;
    int INTO_CATCH = 101;
    int END_TRY = 102;

    int END_RETURN = 105;
    int THROW_EXP = 106;
}
