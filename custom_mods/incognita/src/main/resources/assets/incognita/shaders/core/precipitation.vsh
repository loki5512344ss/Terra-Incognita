#version 330 core

// Rain streaks, snow flakes and ground splashes in a cylinder that follows the camera. Particles are world anchored
// (they do not move with the camera), wrap around the camera in a cell, and stop at the cover height texture, so
// nothing falls under a roof. Nothing is uploaded per frame except uniforms.

layout(location = 0) in vec2 in_vert; // -0.5..0.5 quad
layout(location = 1) in vec4 in_seed; // x, z in the cell, fall phase, per particle roll

uniform mat4 u_view;      // camera rotation
uniform mat4 u_proj;
uniform vec3 u_camera;
uniform vec2 u_wind;      // blocks per second, current gust included
uniform vec2 u_drift;     // integrated wind displacement (no jump when the wind changes)
uniform float u_time;     // seconds
uniform float u_intensity;
uniform float u_mode;     // 0 rain, 1 snow, 2 splash
uniform float u_radius;   // half width of the particle cylinder
uniform float u_height;   // height of the particle cylinder
uniform sampler2D u_cover; // first blocked height per column
uniform vec3 u_grid;      // cover grid origin x, z and size

out vec2 v_uv;
out float v_fade;
out float v_phase;
out float v_roll;

float coverAt(vec2 xz) {
    ivec2 cell = ivec2(floor(xz)) - ivec2(u_grid.xy);
    int size = int(u_grid.z);
    if (cell.x < 0 || cell.y < 0 || cell.x >= size || cell.y >= size) {
        return -1000.0;
    }
    return texelFetch(u_cover, cell, 0).r;
}

void main() {
    int mode = int(u_mode + 0.5);
    float cell = u_radius * 2.0;
    float roll = in_seed.w;
    float fallSpeed = mode == 1 ? mix(1.1, 2.6, roll) : mix(17.0, 26.0, roll);

    vec2 base = in_seed.xy * cell;
    vec2 drift = u_drift * (mode == 1 ? 0.6 : 1.0);
    vec2 xz = mod(base + drift - u_camera.xz, cell) - u_radius + u_camera.xz;

    // Adding the camera height inside the mod keeps each drop at a fixed world height while the camera moves.
    float fall = mod(in_seed.z * u_height + u_time * fallSpeed + u_camera.y, u_height);
    float fallFrac = fall / u_height;
    float y = u_camera.y + u_height * 0.5 - fall;

    if (mode == 1) {
        float t = u_time * (0.5 + 0.5 * roll) + in_seed.z * 40.0;
        xz += vec2(sin(t * 1.3), cos(t * 1.7 + roll * 6.0)) * 0.5
            + vec2(sin(t * 0.37 + roll * 9.0), cos(t * 0.29)) * 0.9;
    }

    float cover = coverAt(xz);
    vec3 world;
    float radial = length(xz - u_camera.xz);
    float fade = 1.0 - smoothstep(u_radius * 0.6, u_radius, radial);
    fade *= u_intensity;

    if (mode == 2) {
        // Splashes stay where they are: no wind drift, so the cell is anchored to the world.
        xz = mod(base - u_camera.xz, cell) - u_radius + u_camera.xz;
        cover = coverAt(xz);
        radial = length(xz - u_camera.xz);
        fade = u_intensity;
        // Splashes sit on the surface, in a smaller radius, each one cycling through an expanding ring.
        float rate = 1.6 + 1.4 * roll;
        float phase = fract(u_time * rate + in_seed.z * 7.0);
        v_phase = phase;
        float live = step(-60.0, cover);
        fade *= 1.0 - smoothstep(u_radius * 0.35, u_radius * 0.6, radial);
        fade *= live * (1.0 - phase) * (1.0 - phase);
        float size = 0.18 + 0.32 * phase * (0.6 + 0.6 * roll);
        world = vec3(xz.x, cover + 0.03, xz.y) + vec3(in_vert.x * size * 2.0, 0.0, in_vert.y * size * 2.0);
        v_uv = in_vert + 0.5;
        v_fade = fade;
        v_roll = roll;
        gl_Position = u_proj * u_view * vec4(world - u_camera, 1.0);
        return;
    }

    world = vec3(xz.x, y, xz.y);
    // Stops at the surface, with a short fade near it so drops do not pop out; hidden indoors.
    float above = y - cover;
    fade *= smoothstep(0.0, 0.5, above);
    // Hide the wrap at the top and bottom of the cylinder.
    fade *= smoothstep(0.0, 0.06, fallFrac) * (1.0 - smoothstep(0.92, 1.0, fallFrac));

    vec3 rel = world - u_camera;
    float distance = length(rel);
    fade *= smoothstep(0.4, 1.8, distance);

    if (mode == 1) {
        vec3 right = vec3(u_view[0][0], u_view[1][0], u_view[2][0]);
        vec3 up = vec3(u_view[0][1], u_view[1][1], u_view[2][1]);
        // Larger flakes are rarer; a slow shimmer keeps them from looking static.
        float size = (0.06 + 0.13 * roll * roll) * (1.0 + 0.05 * distance);
        size *= 0.85 + 0.15 * sin(u_time * 3.0 + roll * 31.0);
        world += right * (in_vert.x * size) + up * (in_vert.y * size);
    } else {
        vec3 velocity = normalize(vec3(u_wind.x * 0.3, -fallSpeed, u_wind.y * 0.3));
        vec3 toCamera = normalize(rel + vec3(0.0001));
        vec3 right = normalize(cross(velocity, toCamera));
        // Streak length and width vary per drop; width grows with distance so far rain does not alias away.
        float length = (0.35 + 1.15 * roll * roll) * (1.0 + 0.04 * distance);
        float width = 0.012 + 0.016 * roll + 0.0025 * distance;
        world += right * (in_vert.x * width) + velocity * (in_vert.y * length);
        world += velocity * (length * 0.4);
    }

    v_uv = in_vert + 0.5;
    v_fade = fade;
    v_roll = roll;
    v_phase = 0.0;
    gl_Position = u_proj * u_view * vec4(world - u_camera, 1.0);
}
