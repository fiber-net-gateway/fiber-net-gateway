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
    int IDX_GET = 15;//!
    int IDX_SET = 16;//!
    int IDX_SET_1 = 17;//!
    int PROP_GET = 18;//!
    int PROP_SET = 19;//! {a:1}
    int PROP_SET_1 = 20;//! {a:1}

    // hack 必需保持递增
    int BOP_PLUS = 25;
    int BOP_MINUS = 26;
    int BOP_MULTIPLY = 27;
    int BOP_DIVIDE = 28;
    int BOP_MOD = 29;
    int BOP_MATCH = 30; //~

    int BOP_LT = 31; //<
    int BOP_LTE = 32; //<=
    int BOP_GT = 33; //~
    int BOP_GTE = 34; //~
    int BOP_EQ = 35; //~
    int BOP_SEQ = 36; //~
    int BOP_NE = 37; //~
    int BOP_SNE = 38; //~
    int BOP_IN = 39; // in

    // hack 必需保持递增
    int UNARY_PLUS = 43;
    int UNARY_MINUS = 44;
    int UNARY_NEG = 45;//!
    int UNARY_TYPEOF = 46;//!

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
