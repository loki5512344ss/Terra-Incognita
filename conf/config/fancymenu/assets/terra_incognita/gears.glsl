// Terra Incognita: фон главного меню. Диорама: механизм из сцепленных шестерён на площадке,
// камера медленно облетает его по кругу. Стиль Minecraft: плоские грани, пиксельные текстуры.
// Генерируется local_script/build-menu.py, таблицы шестерён и стоек подставляются ниже.
const float TAU = 6.28318530;
const float TOOTH_ROOT = 1.15;
const float TOOTH_TIP = 0.80;
const float TEXELS = 22.0;
const float FLOOR_Y = -0.25;
const float FLOOR_R = 3.05;
const vec3 FLOOR_C = vec3(0.0, 0.0, 0.1);
const float ORBIT_SECONDS = 150.0;
const vec3 NEEDLE_C = vec3(0.2, 2.86, 0.3);
const float OUTLINE = 0.0030;             // толщина обводки (доля расстояния до камеры)
const int NG = 12;
const int NP = 8;
const vec4 GEAR[12] = vec4[12](
    vec4(0.20000, 0.00000, 0.30000, 16.0),
    vec4(-1.08865, 0.00000, 1.04400, 8.0),
    vec4(1.43486, 0.00000, -0.73617, 10.0),
    vec4(-1.64659, 0.00000, 0.37907, 6.0),
    vec4(-0.50000, 1.10000, -1.75000, 12.0),
    vec4(0.44990, 1.89706, -1.75000, 8.0),
    vec4(-1.60696, 1.50290, -1.75000, 7.0),
    vec4(-2.30000, 1.00000, 0.50000, 10.0),
    vec4(-2.30000, 1.85491, 1.21735, 8.0),
    vec4(-2.30000, 1.17226, -0.47693, 6.0),
    vec4(0.20000, 2.30000, 0.30000, 10.0),
    vec4(1.19200, 2.30000, 0.30000, 6.0));
const vec4 GEXT[12] = vec4[12](
    vec4(0.20000, 0.15000, 0.06200, 0.08500),
    vec4(0.77810, -0.30000, 0.06200, 0.08500),
    vec4(0.06397, -0.24000, 0.06200, 0.08500),
    vec4(0.47516, 0.40000, 0.06200, 0.08500),
    vec4(0.20000, 0.20000, 0.06200, 0.08500),
    vec4(0.26723, -0.30000, 0.06200, 0.08500),
    vec4(0.05608, -0.34286, 0.06200, 0.08500),
    vec4(0.20000, 0.24000, 0.06200, 0.08500),
    vec4(0.53540, -0.30000, 0.06200, 0.08500),
    vec4(0.77204, -0.40000, 0.06200, 0.08500),
    vec4(0.20000, 0.24000, 0.06200, 0.08500),
    vec4(0.19027, -0.40000, 0.06200, 0.08500));
const int GAX[12] = int[12](1, 1, 1, 1, 2, 2, 2, 0, 0, 0, 1, 1);
const float GTONE[12] = float[12](0.0, 1.0, 2.0, 0.0, 1.0, 0.0, 2.0, 0.0, 2.0, 1.0, 1.0, 0.0);
const vec3 POSTC[8] = vec3[8](vec3(-0.50000, 0.42500, -2.07500), vec3(0.44990, 0.82353, -2.07500), vec3(-1.60696, 0.62645, -2.07500), vec3(-2.62500, 0.37500, 0.50000), vec3(-2.62500, 0.80245, 1.21735), vec3(-2.62500, 0.46113, -0.47693), vec3(0.20000, 1.10000, 0.30000), vec3(0.69600, 2.05000, 0.30000));
const vec3 POSTH[8] = vec3[8](vec3(0.09000, 0.76500, 0.09000), vec3(0.09000, 1.16353, 0.09000), vec3(0.09000, 0.96645, 0.09000), vec3(0.09000, 0.71500, 0.09000), vec3(0.09000, 1.14245, 0.09000), vec3(0.09000, 0.80113, 0.09000), vec3(0.07500, 1.20000, 0.07500), vec3(0.56000, 0.05000, 0.06000));

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x), mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { s += a * vnoise(p); p = p * 2.03 + 17.1; a *= 0.5; }
    return s;
}
float mapHeight(vec2 c) { return fbm(c * 0.045 + 40.0); }

// стрелка компаса над механизмом: не может найти север и рыскает
float needleAngle(float time) {
    return time * 0.12 + 0.9 * sin(time * 0.61) + 0.45 * sin(time * 1.73 + 1.0);
}
vec3 needleLocal(vec3 p, float time) {
    vec3 q = p - NEEDLE_C;
    float a = needleAngle(time);
    float c = cos(a), s = sin(a);
    return vec3(c * q.x - s * q.z, q.y, s * q.x + c * q.z);
}

// ось шестерни -> локальная ось Z (перестановка сама себе обратна)
vec3 toAxis(vec3 v, int ax) {
    return ax == 2 ? v : (ax == 1 ? v.xzy : v.zyx);
}

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdGear(vec3 p, float n, float ang, float m, float hz) {
    float R = m * n;
    float th = 2.1 * m;
    float l = length(p.xy);
    float a = atan(p.y, p.x) - ang;
    float seg = TAU / n;
    float q = a - seg * floor(a / seg + 0.5);
    vec2 tp = vec2(cos(q), sin(q)) * l;
    float k = clamp((tp.x - (R - th * 0.5)) / th, 0.0, 1.0);
    vec2 bq = vec2(abs(tp.x - (R - 0.3 * m)) - (th * 0.5 + m * 0.1), abs(tp.y) - mix(TOOTH_ROOT, TOOTH_TIP, k) * m);
    float tooth = (length(max(bq, 0.0)) + min(max(bq.x, bq.y), 0.0)) * 0.85;
    float d2 = min(l - (R - th * 0.5), tooth);
    float rin = max(0.26 * R, 2.2 * m);
    float rout = R - th * 0.5 - 1.5 * m;
    float face = hz;
    float hub = max(0.22 * R, 2.0 * m);
    if (l < hub) face = hz * 1.45;
    if (rout - rin > 1.5 * m) {
        face -= hz * 0.42 * step(0.0, min(l - rin, rout - l));
        if (n > 9.5) {
            float ns = n > 13.0 ? 6.0 : 5.0;
            float sseg = TAU / ns;
            float sa = a - sseg * floor(a / sseg + 0.5);
            vec2 sp = vec2(cos(sa), sin(sa)) * l;
            float rc = 0.5 * (rin + rout);
            float hr = 0.5 * (rout - rin) - 0.7 * m;
            vec2 hq = vec2(abs(sp.x - rc) - hr * 0.55, abs(sp.y) - hr * 0.5 * (sp.x / rc));
            float hole = length(max(hq, 0.0)) + min(max(hq.x, hq.y), 0.0) - hr * 0.45;
            d2 = max(d2, -hole);
        }
    }
    vec2 w2 = vec2(d2, abs(p.z) - face);
    return min(max(w2.x, w2.y), 0.0) + length(max(w2, 0.0));
}

// x — расстояние, y — материал: 0..NG-1 шестерни, 100 вал, 101 стойка, 102 площадка
vec2 map(vec3 p, float time) {
    vec2 res = vec2(1e4, -1.0);
    float fl = max(length(p.xz - FLOOR_C.xz) - FLOOR_R, abs(p.y - (FLOOR_Y - 0.09)) - 0.09);
    res = vec2(fl, 102.0);
    // стрелка компаса и её ось
    vec3 nq = needleLocal(p, time);
    float nd = max((abs(nq.x) / 1.05 + abs(nq.z) / 0.24 - 1.0) * 0.21, abs(nq.y) - 0.045);
    if (nd < res.x) res = vec2(nd, 103.0);
    float pin = max(length(nq.xz) - 0.07, abs(nq.y + 0.06) - 0.12);
    if (pin < res.x) res = vec2(pin, 100.0);
    for (int i = 0; i < NP; i++) {
        float d = sdBox(p - POSTC[i], POSTH[i]);
        if (d < res.x) res = vec2(d, 101.0);
    }
    for (int i = 0; i < NG; i++) {
        vec4 g = GEAR[i];
        vec4 e = GEXT[i];
        vec3 q = toAxis(p - g.xyz, GAX[i]);
        float R = e.z * g.w;
        float lxy = length(q.xy);
        float sh = max(max(abs(q.x), abs(q.y)) - 0.85 * e.z, abs(q.z) - (e.w + 0.24));
        if (sh < res.x) res = vec2(sh, 100.0);
        float bound = max(lxy - (R + 1.3 * e.z), abs(q.z) - e.w);
        float d = bound;
        if (bound < 0.03) d = sdGear(q, g.w, e.x + e.y * time, e.z, e.w);
        if (d < res.x) res = vec2(d, float(i));
    }
    return res;
}

vec3 calcNormal(vec3 p, float time) {
    vec2 e = vec2(1.0, -1.0) * 0.0012;
    return normalize(e.xyy * map(p + e.xyy, time).x + e.yyx * map(p + e.yyx, time).x +
                     e.yxy * map(p + e.yxy, time).x + e.xxx * map(p + e.xxx, time).x);
}

float shadow(vec3 ro, vec3 rd, float time) {
    float t = 0.03;
    for (int i = 0; i < 48; i++) {
        float h = map(ro + rd * t, time).x;
        if (h < 0.002) return 0.0;
        t += clamp(h, 0.012, 0.25);
        if (t > 5.0) break;
    }
    return 1.0;
}

vec3 backdrop(vec3 rd) {
    float h = exp(-abs(rd.y + 0.05) * 3.2);
    vec3 c = mix(vec3(0.012, 0.008, 0.006), vec3(0.085, 0.050, 0.022), h);
    return c + vec3(0.05, 0.028, 0.010) * pow(max(rd.y, 0.0), 0.6) * 0.35;
}

// плоская нормаль: к ближайшей оси в локальной системе шестерни
vec3 flatNormal(vec3 nl) {
    vec3 an = abs(nl);
    return an.z > max(an.x, an.y) ? vec3(0.0, 0.0, sign(nl.z)) : normalize(vec3(nl.xy, 0.0));
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    vec2 sp = (2.0 * fragCoord - iResolution.xy) / iResolution.y;
    sp.x -= 0.40;                              // сцена правее: слева панель меню
    float time = iTime;

    float orbit = time * TAU / ORBIT_SECONDS + 0.6;
    vec3 ta = vec3(0.0, 0.95, 0.1);
    vec3 ro = ta + vec3(6.7 * sin(orbit), 2.0 + 0.4 * sin(orbit * 2.0), 6.7 * cos(orbit));
    vec3 ww = normalize(ta - ro);
    vec3 uu = normalize(cross(ww, vec3(0.0, 1.0, 0.0)));
    vec3 vv = cross(uu, ww);
    vec3 rd = normalize(sp.x * uu + sp.y * vv + 2.35 * ww);

    vec3 col = backdrop(rd);

    // сфера, в которую вписана вся сцена
    vec3 oc = ro - vec3(0.0, 1.0, 0.1);
    float bq = dot(oc, rd);
    float disc = bq * bq - (dot(oc, oc) - 4.1 * 4.1);
    vec2 hit = vec2(-1.0);
    bool outline = false;
    if (disc > 0.0) {
        float sq = sqrt(disc);
        float t = max(-bq - sq, 0.0);
        float tEnd = -bq + sq;
        float lastH = 1e3;
        for (int i = 0; i < 120; i++) {
            vec2 h = map(ro + rd * t, time);
            if (h.x < 0.0015) { hit = vec2(t, h.y); break; }
            // луч прошёл вплотную к краю и уходит дальше — это контур
            if (lastH < OUTLINE * t && h.x > lastH + 0.0005) { outline = true; break; }
            lastH = h.x;
            t += h.x;
            if (t > tEnd) break;
        }
    }

    if (outline) {
        col = vec3(0.030, 0.018, 0.010);
    } else if (hit.x > 0.0) {
        vec3 p = ro + rd * hit.x;
        vec3 n = calcNormal(p, time);
        vec3 L1 = normalize(vec3(-0.45, 0.80, 0.40));
        vec3 fn; vec3 base;
        if (hit.y > 101.5 && hit.y < 102.5) {
            // площадка: плитка 16x16 пикселей, тёмный кант
            fn = abs(n.y) > 0.7 ? vec3(0.0, sign(n.y), 0.0) : normalize(vec3(n.x, 0.0, n.z));
            // пол — карта, как в Minecraft: разведанное нарисовано, остальное пергамент
            vec2 tx = floor((p.xz - FLOOR_C.xz) * TEXELS);
            float rnd = hash12(tx);
            vec3 paper = vec3(0.36, 0.28, 0.17) * (0.92 + 0.08 * rnd);
            float h = mapHeight(tx);
            float hn = mapHeight(tx + vec2(0.0, 1.0));
            vec3 land;
            if (h < 0.47) land = mix(vec3(0.10, 0.12, 0.36), vec3(0.14, 0.15, 0.50), smoothstep(0.30, 0.47, h));
            else if (h < 0.495) land = vec3(0.55, 0.50, 0.32);
            else if (h < 0.60) land = vec3(0.26, 0.38, 0.11);
            else if (h < 0.66) land = vec3(0.06, 0.25, 0.06);
            else land = vec3(0.27, 0.27, 0.27);
            float sl = h < 0.47 ? 0.0 : (h - hn) * 60.0;
            land *= sl > 0.25 ? 1.0 : (sl < -0.25 ? 0.72 : 0.86);
            float rr = length(tx) / (FLOOR_R * TEXELS);
            float explored = fbm(tx * 0.03 - 9.0) + 0.30 - rr * 0.42;
            base = explored > 0.50 ? mix(paper, land * 0.78, 0.88) : (explored > 0.485 ? paper * 0.6 : paper);
            // роза ветров по центру карты
            vec2 rp = (tx + 0.5) / TEXELS;
            float ra = atan(rp.y, rp.x);
            float rl = length(rp);
            float star = rl - 1.75 * pow(abs(cos(ra * 2.0)), 8.0) - 0.85 * pow(abs(sin(ra * 2.0)), 12.0) - 0.30;
            if (star < 0.0) base = mod(floor(ra / 0.7854 + 8.0), 2.0) < 0.5 ? vec3(0.42, 0.10, 0.07) : vec3(0.20, 0.13, 0.08);
            if (abs(rl - 2.25) < 0.8 / TEXELS) base = vec3(0.20, 0.13, 0.08);
            float rim = length(p.xz - FLOOR_C.xz);
            if (rim > FLOOR_R - 3.0 / TEXELS) base = vec3(0.46, 0.28, 0.08);
            if (fn.y < 0.5) base = vec3(0.30, 0.18, 0.06) * (0.9 + 0.1 * hash12(floor(vec2(atan(p.z, p.x) * 40.0, p.y * TEXELS))));
        } else if (hit.y > 102.5) {
            vec3 nq = needleLocal(p, time);
            fn = abs(n.y) > 0.6 ? vec3(0.0, sign(n.y), 0.0) : normalize(vec3(n.x, 0.0, n.z));
            vec2 tx = floor(nq.xz * TEXELS);
            base = (nq.x > 0.0 ? vec3(0.62, 0.10, 0.07) : vec3(0.62, 0.58, 0.50)) * (0.90 + 0.10 * floor(hash12(tx) * 3.0) / 2.0);
        } else if (hit.y > 100.5) {
            vec3 an = abs(n);
            fn = an.x > max(an.y, an.z) ? vec3(sign(n.x), 0.0, 0.0) : (an.y > an.z ? vec3(0.0, sign(n.y), 0.0) : vec3(0.0, 0.0, sign(n.z)));
            vec3 tx = floor(p * TEXELS + 0.001);
            base = vec3(0.15, 0.10, 0.06) * (0.86 + 0.14 * floor(hash12(tx.xy + tx.z * 13.0) * 3.0) / 2.0);
            if (mod(tx.y, 8.0) < 1.0) base *= 0.7;
        } else if (hit.y > 99.0) {
            vec3 an = abs(n);
            fn = an.x > max(an.y, an.z) ? vec3(sign(n.x), 0.0, 0.0) : (an.y > an.z ? vec3(0.0, sign(n.y), 0.0) : vec3(0.0, 0.0, sign(n.z)));
            vec3 tx = floor(p * TEXELS);
            base = vec3(0.13, 0.135, 0.13) * (0.9 + 0.1 * hash12(tx.xy + tx.z * 5.0));
        } else {
            int gi = int(hit.y + 0.5);
            vec4 g = GEAR[gi];
            vec4 e = GEXT[gi];
            int ax = GAX[gi];
            float tone = GTONE[gi];
            fn = toAxis(flatNormal(toAxis(n, ax)), ax);
            vec3 q = toAxis(p - g.xyz, ax);
            float ang = e.x + e.y * time;
            float ca = cos(-ang), sa = sin(-ang);
            vec2 lp = vec2(ca * q.x - sa * q.y, sa * q.x + ca * q.y);
            vec3 tex = floor(vec3(lp, q.z) * TEXELS + 0.001);
            float rnd = hash12(tex.xy + tex.z * 17.0 + float(gi) * 7.0);
            float rr = length((tex.xy + 0.5) / TEXELS);
            float R = e.z * g.w;
            vec3 c0 = tone < 0.5 ? vec3(0.62, 0.38, 0.11) : (tone < 1.5 ? vec3(0.50, 0.22, 0.10) : vec3(0.33, 0.23, 0.13));
            base = c0 * (0.88 + 0.12 * floor(rnd * 3.0) / 2.0);
            if (length(q.xy) < max(0.22 * R, 2.0 * e.z) + 0.004) base = c0 * 0.55 * (0.92 + 0.08 * floor(rnd * 2.0));
        }
        // освещение как у блоков: у каждой грани своя яркость, тень жёсткая
        float shade = 0.42 + 0.58 * max(dot(fn, L1), 0.0);
        float sh = shadow(p + fn * 0.01, L1, time);
        vec3 lit = base * (shade * mix(0.55, 1.0, sh) + 0.10 * max(fn.y, 0.0));
        float ao = clamp(map(p + fn * 0.07, time).x / 0.07, 0.0, 1.0);
        lit *= mix(0.78, 1.0, smoothstep(0.3, 0.9, ao));
        // дымка к дальнему краю сцены
        float fog = smoothstep(5.5, 11.0, hit.x);
        col = mix(lit, backdrop(rd), fog * 0.45);
    }

    vec2 vg = uv * (1.0 - uv);
    col *= pow(clamp(vg.x * vg.y * 18.0, 0.0, 1.0), 0.20);
    col = pow(col, vec3(0.4545));
    col += (hash12(fragCoord + fract(time) * 61.0) - 0.5) / 255.0;
    fragColor = vec4(col, 1.0);
}
// end
