package dev.sborka.incognita.weather.clouds.render.band;

/** Static vertex data: the bounding box of one puff and the cube around the camera that the composite pass draws. */
public final class BoxMesh {
    /** Unit box (-0.5..0.5) as one 14 vertex triangle strip, positions only. */
    private static final float[] PUFF_BOX = {
        -.5f, +.5f, +.5f,  +.5f, +.5f, +.5f,  -.5f, -.5f, +.5f,  +.5f, -.5f, +.5f,
        +.5f, -.5f, -.5f,  +.5f, +.5f, +.5f,  +.5f, +.5f, -.5f,  -.5f, +.5f, +.5f,
        -.5f, +.5f, -.5f,  -.5f, -.5f, +.5f,  -.5f, -.5f, -.5f,  +.5f, -.5f, -.5f,
        -.5f, +.5f, -.5f,  +.5f, +.5f, -.5f,
    };
    public static final int PUFF_BOX_VERTICES = PUFF_BOX.length / 3;

    /** Cube (-1..1) as 12 triangles, drawn around the camera with culling off. */
    private static final float[] SKY_CUBE = {
        -1, +1, -1, -1, -1, -1, +1, -1, -1, -1, +1, -1, +1, -1, -1, +1, +1, -1,
        -1, +1, -1, +1, +1, -1, +1, +1, +1, -1, +1, -1, +1, +1, +1, -1, +1, +1,
        +1, +1, +1, +1, -1, +1, -1, -1, +1, +1, +1, +1, -1, -1, +1, -1, +1, +1,
        -1, +1, +1, -1, -1, +1, -1, -1, -1, -1, +1, +1, -1, -1, -1, -1, +1, -1,
        +1, -1, -1, -1, -1, -1, -1, -1, +1, +1, -1, -1, -1, -1, +1, +1, -1, +1,
        +1, +1, -1, +1, -1, -1, +1, -1, +1, +1, +1, -1, +1, -1, +1, +1, +1, +1,
    };
    public static final int SKY_CUBE_VERTICES = SKY_CUBE.length / 3;

    private BoxMesh() {}

    public static float[] puffBox() {
        return PUFF_BOX.clone();
    }

    public static float[] skyCube() {
        return SKY_CUBE.clone();
    }
}
