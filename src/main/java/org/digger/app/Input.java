package org.digger.app;

class Input {

    Digger dig;

    boolean leftPressed = false, rightPressed = false, upPressed = false, downPressed = false;
    boolean f1Pressed = false, firePressed = false;
    boolean plusPressed = false, minusPressed = false;
    boolean escape = false;

    int lastKeyCode = 0;
    int lastAsciiKeyCode;
    int dynamicDir = -1, staticDir = -1;
    int keyDir = 0;
    boolean firePressedFlag = false;
    boolean joyFlag = false;

    Input(Digger d) {
        dig = d;
    }

    void checkKeyboard() {
        if (plusPressed) {
            if (dig.frametime > Digger.MIN_RATE)
                dig.frametime -= 5;
            plusPressed = false;
        }
        if (minusPressed) {
            if (dig.frametime < Digger.MAX_RATE)
                dig.frametime += 5;
            minusPressed = false;
        }
    }

    void detectJoystick() {
        joyFlag = false;
        staticDir = dynamicDir = -1;
    }

    int getAsciiKey(int make) {
        if ((make == ' ') || ((make >= 'a') && (make <= 'z')) || ((make >= 'A') && (make <= 'Z'))
                || ((make >= '0') && (make <= '9')))
            return make;
        else
            return 0;
    }

    int getDirection() {
        return keyDir;
    }

    void initKeyboard() {
    }

    void keyDownPressed() {
        downPressed = true;
        dynamicDir = staticDir = 6;
    }

    void keyDownReleased() {
        downPressed = false;
        if (dynamicDir == 6)
            setDirection();
    }

    void keyF1Pressed() {
        firePressed = true;
        f1Pressed = true;
    }

    void keyF1Released() {
        f1Pressed = false;
    }

    void keyLeftPressed() {
        leftPressed = true;
        dynamicDir = staticDir = 4;
    }

    void keyLeftReleased() {
        leftPressed = false;
        if (dynamicDir == 4)
            setDirection();
    }

    void keyRightPressed() {
        rightPressed = true;
        dynamicDir = staticDir = 0;
    }

    void keyRightReleased() {
        rightPressed = false;
        if (dynamicDir == 0)
            setDirection();
    }

    void keyUpPressed() {
        upPressed = true;
        dynamicDir = staticDir = 2;
    }

    void keyUpReleased() {
        upPressed = false;
        if (dynamicDir == 2)
            setDirection();
    }

    void processKey(int key) {
        lastKeyCode = key;
        lastAsciiKeyCode = key;
    }

    /**
     * Reads current direction from input state.
     * Direction is preserved while the key is held,
     * even if the digger can't turn yet (grid alignment).
     */
    void readDirection() {
        if (dynamicDir != -1)
            keyDir = dynamicDir;
        else {
            // No new key pressed — keep last held direction
            if (upPressed) keyDir = 2;
            else if (downPressed) keyDir = 6;
            else if (leftPressed) keyDir = 4;
            else if (rightPressed) keyDir = 0;
            else keyDir = -1;
        }
        firePressedFlag = f1Pressed || firePressed;
        firePressed = false;
    }

    void setDirection() {
        dynamicDir = -1;
        if (upPressed) dynamicDir = staticDir = 2;
        if (downPressed) dynamicDir = staticDir = 6;
        if (leftPressed) dynamicDir = staticDir = 4;
        if (rightPressed) dynamicDir = staticDir = 0;
    }

    boolean testStart() {
        if (lastKeyCode != 0 && (lastKeyCode & 0x80) == 0 && lastKeyCode != 27) {
            joyFlag = false;
            lastKeyCode = 0;
            return true;
        }
        return false;
    }
}
