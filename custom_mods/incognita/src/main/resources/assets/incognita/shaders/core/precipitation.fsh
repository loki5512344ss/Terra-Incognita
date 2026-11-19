#version 330 core

in vec2 v_uv;
in float v_fade;
in float v_phase;
in float v_roll;

uniform float u_mode;
uniform vec3 u_color;
uniform vec3 u_fog_color;
uniform float u_fog; // 0..1 fog density

out vec4 fragColor;

void main() {
    int mode = int(u_mode + 0.5);
    float alpha;
    vec3 color = u_color;
    if (mode == 1) {
        // A soft round flake: bright core with a wide gentle falloff, no hard edge.
        float r = length(v_uv - 0.5) * 2.0;
        alpha = exp(-r * r * 3.2) * (1.0 - smoothstep(0.85, 1.0, r));
        alpha *= 0.75 + 0.25 * v_roll;
        color = mix(color, vec3(1.0), 0.35);
    } else if (mode == 2) {
        // A thin ring that expands and fades, plus a faint centre dot at the start.
        float r = length(v_uv - 0.5) * 2.0;
        float ring = smoothstep(0.12, 0.0, abs(r - (0.25 + 0.7 * v_phase)));
        alpha = ring * 0.55 * (1.0 - smoothstep(0.9, 1.0, r));
        color = mix(color, vec3(1.0), 0.4);
    } else {
        // A tapered streak: pointed at the tail, soft across, brighter at the head.
        float along = v_uv.y;
        float taper = mix(0.15, 1.0, along);
        float across = abs(v_uv.x - 0.5) * 2.0 / taper;
        alpha = (1.0 - smoothstep(0.35, 1.0, across)) * smoothstep(0.0, 0.25, along) * (1.0 - smoothstep(0.85, 1.0, along));
        alpha *= 0.34 + 0.18 * along;
    }
    alpha *= v_fade;
    alpha *= mix(1.0, 0.45, u_fog);
    if (alpha < 0.004) discard;
    fragColor = vec4(mix(color, u_fog_color, u_fog * 0.65), alpha);
}
