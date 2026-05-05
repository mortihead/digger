package org.digger.app;

class Input {

    Digger dig;

    boolean leftpressed = false, rightpressed = false, uppressed = false, downpressed = false;
    boolean f1pressed = false, firepressed = false;
    boolean pluspressed = false, minuspressed = false;
    boolean escape = false;

    int keypressed = 0;
    int akeypressed;
    int dynamicdir = -1, staticdir = -1;
    int keydir = 0;
    boolean firepflag = false;
    boolean joyflag = false;

    Input(Digger d) {
        dig = d;
    }

    void checkkeyb() {
        if (pluspressed) {
            if (dig.frametime > Digger.MIN_RATE)
                dig.frametime -= 5;
            pluspressed = false;
        }
        if (minuspressed) {
            if (dig.frametime < Digger.MAX_RATE)
                dig.frametime += 5;
            minuspressed = false;
        }
    }

    void detectjoy() {
        joyflag = false;
        staticdir = dynamicdir = -1;
    }

    int getasciikey(int make) {
        if ((make == ' ') || ((make >= 'a') && (make <= 'z')) || ((make >= 'A') && (make <= 'Z'))
                || ((make >= '0') && (make <= '9')))
            return make;
        else
            return 0;
    }

    int getdir() {
        return keydir;
    }

    void initkeyb() {
    }

    void Key_downpressed() {
        downpressed = true;
        dynamicdir = staticdir = 6;
    }

    void Key_downreleased() {
        downpressed = false;
        if (dynamicdir == 6)
            setdirec();
    }

    void Key_f1pressed() {
        firepressed = true;
        f1pressed = true;
    }

    void Key_f1released() {
        f1pressed = false;
    }

    void Key_leftpressed() {
        leftpressed = true;
        dynamicdir = staticdir = 4;
    }

    void Key_leftreleased() {
        leftpressed = false;
        if (dynamicdir == 4)
            setdirec();
    }

    void Key_rightpressed() {
        rightpressed = true;
        dynamicdir = staticdir = 0;
    }

    void Key_rightreleased() {
        rightpressed = false;
        if (dynamicdir == 0)
            setdirec();
    }

    void Key_uppressed() {
        uppressed = true;
        dynamicdir = staticdir = 2;
    }

    void Key_upreleased() {
        uppressed = false;
        if (dynamicdir == 2)
            setdirec();
    }

    void processkey(int key) {
        keypressed = key;
        akeypressed = key;
    }

    void readdir() {
        keydir = staticdir;
        if (dynamicdir != -1)
            keydir = dynamicdir;
        staticdir = -1;
        firepflag = f1pressed || firepressed;
        firepressed = false;
    }

    void setdirec() {
        dynamicdir = -1;
        if (uppressed) dynamicdir = staticdir = 2;
        if (downpressed) dynamicdir = staticdir = 6;
        if (leftpressed) dynamicdir = staticdir = 4;
        if (rightpressed) dynamicdir = staticdir = 0;
    }

    boolean teststart() {
        if (keypressed != 0 && (keypressed & 0x80) == 0 && keypressed != 27) {
            joyflag = false;
            keypressed = 0;
            return true;
        }
        return false;
    }
}
