package io.fiber.net.script.run;

public interface Code {

    int NOOP = 1;
    int LOAD_CONST = 3;
    int LOAD_ROOT = 4;
    int DUMP = 5;
    int POP = 6;
    int LOAD_VAR = 7;
    int STORE_VAR = 8;

    int NEW_OBJECT = 10;
    int NEW_ARRAY = 11;
    int EXP_OBJECT = 12;
    int EXP_ARRAY = 13;
    int PUSH_ARRAY = 14;
    //
    int IDX_SET = 15;//!
    int IDX_GET = 16;//!
    int PROP_GET = 17;//!
    int PROP_SET = 18;//! {a:1}
    int PROP_SET_1 = 19;//! {a:1}

    // hack 必需保持递增
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

    // hack 必需保持递增
    int UNARY_PLUS = 40;
    int UNARY_MINUS = 41;
    int UNARY_NEG = 42;//!
    int UNARY_TYPEOF = 43;//!

    //
    //
    int CALL_FUNC = 50;
    int CALL_FUNC_SPREAD = 51;
    int CALL_ASYNC_FUNC = 52;
    int CALL_ASYNC_FUNC_SPREAD = 53;

    int CALL_CONST = 56;
    int CALL_ASYNC_CONST = 57;
    //
    int JUMP = 60;
    int JUMP_IF_FALSE = 61;
    int JUMP_IF_TRUE = 62;


    int ITERATE_INTO = 65;
    int ITERATE_NEXT = 66;
    int ITERATE_KEY = 67;
    int ITERATE_VALUE = 68;

    int INTO_CATCH = 69;

    int THROW_EXP = 75;
    int END_RETURN = 76;
}
