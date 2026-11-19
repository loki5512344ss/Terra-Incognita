#version 330 core
// Ray / box intersection for the blocky cloud boxes. The optical depth is the length of the ray inside the box, so a
// ray that only clips an edge or a corner is thin and translucent while the body is dense.
//   pass 0 (nearest): writes the G-buffer of the nearest box face and its depth (depth test on)
//   pass 1 (thickness): adds the optical depth of the box along the ray (additive, depth test off)

in vec3 v_rel;
flat in vec3 v_center;
flat in vec3 v_radius;
flat in vec2 v_yaw;
flat in vec4 v_heap;
flat in vec3 v_shade;

layout(location = 0) out vec4 out_surface;   // pass 1: the thickness target is bound to this location
layout(location = 1) out vec4 out_detail;

uniform sampler2D u_scene_depth;
uniform mat4 u_view_proj;
uniform vec2 u_depth_range;   // near, far
uniform vec3 u_cam_forward;
uniform vec4 u_fade;          // draw distance, fog fade start, fog fade end, fog hide-all
uniform float u_pass;
uniform float u_band;
uniform float u_res_scale;     // 1 at full resolution, 2 when the cloud targets are half size

/** Distance along the ray to the scene behind this pixel; very far for the sky. */
float sceneDistance(vec3 dir) {
    float depth;
    if (u_res_scale > 1.5) {
        // One cloud pixel covers 2 x 2 scene pixels: take the farthest, so no cloud is lost at a terrain edge (the
        // full resolution composite depth test still hides the cloud exactly behind the terrain).
        ivec2 size = textureSize(u_scene_depth, 0) - 1;
        ivec2 base = ivec2(gl_FragCoord.xy) * 2;
        depth = max(max(texelFetch(u_scene_depth, min(base, size), 0).r,
                        texelFetch(u_scene_depth, min(base + ivec2(1, 0), size), 0).r),
                    max(texelFetch(u_scene_depth, min(base + ivec2(0, 1), size), 0).r,
                        texelFetch(u_scene_depth, min(base + ivec2(1, 1), size), 0).r));
    } else {
        depth = texelFetch(u_scene_depth, ivec2(gl_FragCoord.xy), 0).r;
    }
    if (depth >= 1.0) return 1.0e9;
    float near = u_depth_range.x;
    float far = u_depth_range.y;
    float z = depth * 2.0 - 1.0;
    float eye = 2.0 * near * far / (far + near - z * (far - near));
    return eye / max(dot(dir, u_cam_forward), 1.0e-3);
}

vec3 toLocal(vec3 v) {
    return vec3(v.x * v_yaw.x + v.z * v_yaw.y, v.y, -v.x * v_yaw.y + v.z * v_yaw.x);
}

vec3 toWorld(vec3 v) {
    return vec3(v.x * v_yaw.x - v.z * v_yaw.y, v.y, v.x * v_yaw.y + v.z * v_yaw.x);
}

void main() {
    vec3 dir = normalize(v_rel);
    vec3 o = toLocal(-v_center);
    vec3 d = toLocal(dir);
    vec3 inv = 1.0 / (abs(d) + 1.0e-6) * sign(d + 1.0e-9);
    vec3 t1 = (-v_radius - o) * inv;
    vec3 t2 = (v_radius - o) * inv;
    vec3 tNear = min(t1, t2);
    vec3 tFar = max(t1, t2);
    float enter = max(max(tNear.x, tNear.y), tNear.z);
    float leave = min(min(tFar.x, tFar.y), tFar.z);
    float t0 = max(enter, 0.0);
    float t1c = min(leave, sceneDistance(dir));
    if (t1c <= t0) discard;
    float optical = (t1c - t0) * v_shade.z * v_shade.z;

    if (u_pass > 0.5) {
        out_surface = vec4(optical, 0.0, 0.0, 0.0);
        return;
    }

    // A cube that is still fading in does not define the surface yet.
    if (v_shade.z < 0.45) discard;
    float tEntry = max(t0, u_depth_range.x * 2.0);
    vec3 hit = dir * tEntry;
    // Normal of the entry face; from inside a box, the face looks at the camera.
    vec3 local;
    if (enter == tNear.x) local = vec3(-sign(d.x), 0.0, 0.0);
    else if (enter == tNear.y) local = vec3(0.0, -sign(d.y), 0.0);
    else local = vec3(0.0, 0.0, -sign(d.z));
    vec3 n = enter <= 0.0 ? -dir : toWorld(local);

    float height = clamp((hit.y - v_heap.y) / max(v_heap.w, 1.0), 0.0, 1.0);
    // Crevices: a face that looks into the heap is tucked between other boxes and gets less sky light.
    vec3 core = vec3(v_heap.x, v_heap.y + v_heap.w * 0.45, v_heap.z);
    float facing = dot(n, normalize(hit - core));
    float ao = v_shade.x * mix(0.78, 1.0, smoothstep(-0.6, 0.5, facing));

    float horizontal = length(hit.xz);
    float far = smoothstep(u_fade.x * 0.68, u_fade.x * 0.98, horizontal);
    vec4 clip = u_view_proj * vec4(hit, 1.0);
    float plane = smoothstep(u_depth_range.y * 0.78, u_depth_range.y * 0.97, clip.w);
    float fog = max(u_fade.w, smoothstep(u_fade.y, u_fade.z, length(hit)));
    float fade = max(max(far, plane), fog);

    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
    out_surface = vec4(n * 0.5 + 0.5, height);
    out_detail = vec4(ao, fade, v_shade.y, u_band / 3.0);
}
