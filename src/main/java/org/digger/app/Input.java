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

    void detectJoystick() {
        joyflag = false;
        staticdir = dynamicdir = -1;
    }

    int getAsciiKey(int make) {
        if ((make == ' ') || ((make >= 'a') && (make <= 'z')) || ((make >= 'A') && (make <= 'Z'))
                || ((make >= '0') && (make <= '9')))
            return make;
        else
            return 0;
    }

    int getDirection() {
        return keydir;
    }

    void initKeyboard() {
    }

    void keyDownPressed() {
        downpressed = true;
        dynamicdir = staticdir = 6;
    }

    void keyDownReleased() {
        downpressed = false;
        if (dynamicdir == 6)
            setDirection();
    }

    void keyF1Pressed() {
        firepressed = true;
        f1pressed = true;
    }

    void keyF1Released() {
        f1pressed = false;
    }

    void keyLeftPressed() {
        leftpressed = true;
        dynamicdir = staticdir = 4;
    }

    void keyLeftReleased() {
        leftpressed = false;
        if (dynamicdir == 4)
            setDirection();
    }

    void keyRightPressed() {
        rightpressed = true;
        dynamicdir = staticdir = 0;
    }

    void keyRightReleased() {
        rightpressed = false;
        if (dynamicdir == 0)
            setDirection();
    }

    void keyUpPressed() {
        uppressed = true;
        dynamicdir = staticdir = 2;
    }

    void keyUpReleased() {
        uppressed = false;
        if (dynamicdir == 2)
            setDirection();
    }

    void processkey(int key) {
        keypressed = key;
        akeypressed = key;
    }

    /**
     * Reads current direction from input state.
     * Direction is preserved while the key is held,
     * even if the digger can't turn yet (grid alignment).
     */
    void readDirection() {
        if (dynamicdir != -1)
            keydir = dynamicdir;
        else {
            // No new key pressed — keep last held direction
            if (uppressed) keydir = 2;
            else if (downpressed) keydir = 6;
            else if (leftpressed) keydir = 4;
            else if (rightpressed) keydir = 0;
            else keydir = -1;
        }
        firepflag = f1pressed || firepressed;
        firepressed = false;
    }

    void setDirection() {
        dynamicdir = -1;
        if (uppressed) dynamicdir = staticdir = 2;
        if (downpressed) dynamicdir = staticdir = 6;
        if (leftpressed) dynamicdir = staticdir = 4;
        if (rightpressed) dynamicdir = staticdir = 0;
    }

    boolean testStart() {
        if (keypressed != 0 && (keypressed & 0x80) == 0 && keypressed != 27) {
            joyflag = false;
            keypressed = 0;
            return true;
        }
        return false;
    }
}
