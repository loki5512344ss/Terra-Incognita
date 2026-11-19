#version 330 core
// Cube around the camera; every pixel gets its world view direction for the composite.

layout(location = 0) in vec3 in_vert;

out vec3 v_dir;

uniform mat4 u_view_proj;

void main() {
    v_dir = in_vert;
    gl_Position = u_view_proj * vec4(in_vert, 1.0);
    gl_Position.z = 0.0;
}
