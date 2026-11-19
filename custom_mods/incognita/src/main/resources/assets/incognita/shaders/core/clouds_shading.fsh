#version 330 core
// Composite: one colour per pixel from the nearest surface and the summed optical depth, blended once into the scene.
// Fair weather clouds stay bright everywhere (gentle cool tint for the underside and crevices); only greyness (rain,
// storm) darkens them.

in vec3 v_dir;

layout(location = 0) out vec4 out_color;

uniform sampler2D u_surface;    // normal xyz, height in heap
uniform sampler2D u_detail;     // exposure, fade, variance, band
uniform sampler2D u_thickness;  // optical depth
uniform sampler2D u_depth;      // nearest cloud depth

uniform vec3 u_light_dir;       // sun, or the moon at night
uniform vec3 u_sun;
uniform vec3 u_sky_top;
uniform vec3 u_sky_bottom;
uniform vec3 u_haze;            // fog colour = the sky's horizon colour
uniform vec4 u_params;          // sigma, max alpha, greyness, sunsetness
uniform float u_sun_height;     // sine of the sun elevation
uniform float u_res_scale;      // size of the screen relative to the cloud targets (1 or 2)
uniform float u_fog_hide;       // share of the clouds the dense fog hides whatever the distance

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float phaseHG(float cosTheta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (12.566 * pow(max(1.0 + g2 - 2.0 * g * cosTheta, 1.0e-4), 1.5));
}

void main() {
    // The optical depth is filtered (bilinear), so a half resolution target still gives smooth cloud edges.
    vec2 cloudPos = gl_FragCoord.xy / u_res_scale;
    float tau = texture(u_thickness, cloudPos / vec2(textureSize(u_thickness, 0))).r;
    if (tau < 0.02) discard;
    // The filtered edge can reach a texel without a nearest surface: take the surface of a covered neighbour of
    // the filter footprint, so the edge keeps its fade (no ghost rims in fog or at the far edge).
    ivec2 last = textureSize(u_surface, 0) - 1;
    ivec2 corner = ivec2(floor(cloudPos - 0.5));
    ivec2 px = clamp(ivec2(cloudPos), ivec2(0), last);
    vec4 surface = texelFetch(u_surface, px, 0);
    for (int i = 0; i < 4 && surface == vec4(0.0); i++) {
        px = clamp(corner + ivec2(i & 1, i >> 1), ivec2(0), last);
        surface = texelFetch(u_surface, px, 0);
    }
    if (surface == vec4(0.0)) discard;
    vec4 detail = texelFetch(u_detail, px, 0);

    float sigma = u_params.x;
    float grey = u_params.z;
    float sunset = u_params.w;
    float alpha = u_params.y * (1.0 - exp(-tau * sigma));

    vec3 V = normalize(v_dir);
    vec3 L = normalize(u_light_dir);
    vec3 n = normalize(surface.rgb * 2.0 - 1.0);
    float h = surface.a;
    float exposure = detail.r > 0.0 ? detail.r : 0.85;
    float fade = detail.g;
    float variance = detail.b;
    float band = detail.a * 3.0;
    float calm = 1.0 - grey;

    // Low in the heap, side faces of the cubes take the light of the underside, so the belly reads as one soft
    // surface instead of a lattice of bright cube edges.
    float lowPart = 1.0 - smoothstep(0.05, 0.40, h);
    vec3 shadeN = normalize(mix(n, vec3(0.0, -1.0, 0.0), lowPart * 0.40 * (1.0 - max(n.y, 0.0))));
    // Ambient: sky light grows with the height inside the heap and with an upward facing normal.
    float up = shadeN.y * 0.5 + 0.5;
    vec3 ambient = mix(u_sky_bottom, u_sky_top, clamp(0.30 + 0.25 * up + 0.40 * h, 0.0, 1.0));

    // Direct light, wrapped: light scatters through the whole cloud, so the side away from the sun keeps more than
    // half of it in fair weather. Higher bands keep the sun a little longer after sunset (their horizon is farther).
    float ndl = dot(shadeN, L);
    // Rain light is diffuse: the faces of the deck barely differ.
    float wrap = mix(clamp((ndl + 0.65) / 1.65, 0.0, 1.0), 0.5, grey * 0.8);
    float lit = smoothstep(-0.10 - 0.035 * band, 0.02, u_sun_height) + (1.0 - step(-0.12, u_sun_height));
    float direct = mix(0.62, 1.0, wrap) * clamp(lit, 0.0, 1.0);
    // Rain clouds self shadow: the thicker and the lower, the darker.
    direct *= 1.0 - grey * 0.3 * smoothstep(4.0, 30.0, tau) * (1.0 - 0.5 * h);

    // Silver lining: thin parts towards the sun glow (forward scattering), thick cores do not.
    float thin = exp(-tau * sigma * 0.8);
    float lining = phaseHG(dot(V, L), 0.6) * 4.0 * thin * calm;

    vec3 color = ambient * (0.52 + 0.40 * grey) + u_sun * (direct * 0.62 + lining * 0.30);

    // Volume cues without darkness: a cool tint toward the underside and into crevices, a warm lift on top.
    float occlusion = clamp((1.0 - exposure) * 1.5, 0.0, 0.55);
    float belly = (1.0 - smoothstep(0.0, 0.40, h)) * smoothstep(0.2, -0.6, shadeN.y);
    color *= mix(vec3(1.0), vec3(0.89, 0.92, 0.99), clamp(occlusion + 0.5 * belly, 0.0, 1.0) * calm);
    color *= 1.0 + 0.07 * calm * smoothstep(0.55, 1.0, h) * max(ndl, 0.0);
    // Rain: heavy grey bellies.
    color *= 1.0 - grey * (0.08 + 0.20 * belly + 0.08 * (1.0 - h));

    // Sunset: pastel gradient by height and by the side facing the sun, never one flat colour.
    vec2 vh = normalize(V.xz + vec2(1.0e-4));
    vec2 lh = normalize(L.xz + vec2(1.0e-4));
    float sunSide = 0.5 + 0.5 * dot(vh, lh);
    float faceSun = clamp(ndl * 0.5 + 0.5, 0.0, 1.0);
    // Four pastel stops: lavender in the shade low and away from the sun, rose in the middle, orange towards the sun
    // and gold on the sunlit tops. Height in the heap and the side facing the sun pick the stop.
    vec3 gold = vec3(1.00, 0.84, 0.52);
    vec3 orange = vec3(1.00, 0.66, 0.44);
    vec3 rose = vec3(0.98, 0.62, 0.70);
    vec3 lavender = vec3(0.68, 0.60, 0.86);
    // Clouds towards the sun glow orange and gold, clouds away from it stay rose and lavender.
    float warmth = clamp(0.80 * sunSide + 0.35 * faceSun - 0.10, 0.0, 1.0);
    // The low sun lights the clouds from below as well: towards the sun even the underside turns orange.
    float lift = smoothstep(0.0, 1.0, 0.60 * h + 0.25 * faceSun + 0.45 * warmth * (1.0 - h));
    vec3 low = mix(lavender, rose, smoothstep(0.10, 0.70, warmth));
    vec3 high = mix(rose, mix(orange, gold, lift), smoothstep(0.25, 0.85, warmth));
    vec3 tint = mix(low, high, lift);
    tint = mix(tint, vec3(1.0), 0.10);
    float brightness = luma(color);
    color = mix(color, tint * (0.32 + 0.92 * brightness), sunset * 0.85 * calm);

    color *= 1.0 + (variance - 0.5) * 0.03 * calm;

    // Far clouds melt into the horizon haze; distance and fog fade them out smoothly.
    float horizon = pow(1.0 - clamp(abs(V.y), 0.0, 1.0), 4.0);
    color = mix(color, u_haze, horizon * 0.45);
    color = mix(color, u_haze, fade);
    alpha *= (1.0 - fade) * (1.0 - u_fog_hide);

    out_color = vec4(color, alpha);
    gl_FragDepth = texelFetch(u_depth, px, 0).r;
}
