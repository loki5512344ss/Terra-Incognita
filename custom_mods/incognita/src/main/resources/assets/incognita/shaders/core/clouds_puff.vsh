#version 330 core
// One cloud box ("puff") per instance. The box is drawn slightly enlarged; the fragment shader intersects the ray.

layout(location = 0) in vec3 in_vert;    // unit box corner, -0.5..0.5
layout(location = 1) in vec4 in_pos;     // centre (x, z relative to the band anchor; y world), strength
layout(location = 2) in vec4 in_radius;  // half sizes, heap seed
layout(location = 3) in vec4 in_heap;    // heap centre x, heap base y, heap centre z, heap height
layout(location = 4) in vec4 in_orient;  // cos yaw, sin yaw, exposure, growth offset

uniform mat4 u_view_proj;     // projection * view rotation; positions are camera relative
uniform vec3 u_origin;        // camera-relative band anchor (x, z) and -camera y
uniform float u_cover_shift;  // live strength shift: built threshold - current threshold
uniform float u_time;         // seconds
uniform float u_curvature;    // vertical drop per squared horizontal block

out vec3 v_rel;               // camera-relative point on the box
flat out vec3 v_center;
flat out vec3 v_radius;
flat out vec2 v_yaw;
flat out vec4 v_heap;         // camera-relative heap centre (x, z), heap base (y), heap height
flat out vec3 v_shade;        // exposure, per puff variance, growth

const float EVOLVE = 0.07;
const float GROW_BAND = 0.16;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    float seed = in_radius.w;
    // Each heap breathes on its own slow cycle (3-6 minutes), so clouds build up and dissolve over time.
    float cycle = 180.0 + 180.0 * seed;
    float strength = in_pos.w + u_cover_shift + EVOLVE * sin(u_time * 6.2831853 / cycle + seed * 6.2831853);
    float grow = smoothstep(in_orient.w, in_orient.w + GROW_BAND, strength);
    if (grow < 0.01) {
        // Not grown yet: a degenerate primitive outside the clip volume.
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        v_rel = vec3(0.0);
        v_center = vec3(0.0);
        v_radius = vec3(1.0);
        v_yaw = vec2(1.0, 0.0);
        v_heap = vec4(0.0, 0.0, 0.0, 1.0);
        v_shade = vec3(0.0);
        return;
    }

    vec3 heap = vec3(in_heap.x, in_heap.y, in_heap.z) + u_origin;
    vec3 c = in_pos.xyz + u_origin;
    // A growing heap swells out from its middle and rises from its base.
    c.xz = heap.xz + (c.xz - heap.xz) * mix(0.85, 1.0, grow);
    c.y = heap.y + (c.y - heap.y) * mix(0.80, 1.0, grow);
    // Cubes keep most of their size and fade in instead, so a forming cloud is never a spray of tiny boxes.
    vec3 r = in_radius.xyz * mix(0.70, 1.0, grow);

    // Earth curvature: distant clouds sink towards the horizon.
    float drop = dot(c.xz, c.xz) * u_curvature;
    c.y -= drop;
    heap.y -= drop;

    vec3 local = in_vert * 2.02 * r;
    vec2 yaw = in_orient.xy;
    vec3 world = vec3(local.x * yaw.x - local.z * yaw.y, local.y, local.x * yaw.y + local.z * yaw.x);
    v_rel = c + world;
    v_center = c;
    v_radius = r;
    v_yaw = yaw;
    v_heap = vec4(heap.x, heap.y, heap.z, in_heap.w);
    // The offset inside the heap does not depend on the window anchor, so the variance is stable.
    v_shade = vec3(in_orient.z, hash12(floor((in_pos.xz - in_heap.xz) * 4.0) + vec2(seed * 97.0)), grow);
    gl_Position = u_view_proj * vec4(v_rel, 1.0);
}
