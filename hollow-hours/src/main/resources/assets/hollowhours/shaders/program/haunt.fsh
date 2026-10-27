#version 150

// Hollow Hours horror grade: drained cold colours, crushed shadows, dark vignette, film grain,
// colour fringes and a heartbeat pulse with fear, everything soaked red under a blood moon.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float Clock;     // seconds
uniform float Fear;      // 0..1
uniform float Blood;     // 0..1 blood moon
uniform float Fog;       // 0..1
uniform float Strength;  // 0..1.5
uniform float Flashing;  // 0 = photosensitive mode

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(443.897, 441.423));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

void main() {
    float s = Strength;
    vec2 c = texCoord - 0.5;
    float r2 = dot(c, c);

    // heartbeat: a double thump, faster and harder the more afraid you are
    float bpm = mix(55.0, 140.0, Fear);
    float ph = fract(Clock * bpm / 60.0);
    float beat = exp(-ph * 22.0) + 0.6 * exp(-max(ph - 0.18, 0.0) * 22.0) * step(0.18, ph);
    beat *= smoothstep(0.45, 1.0, Fear) * Flashing;
    vec2 uv = texCoord - c * 0.010 * beat * s;

    // colour fringes toward the edges
    float ca = (0.0012 + 0.0065 * Fear + 0.002 * Blood) * s * (0.4 + r2 * 3.0);
    vec3 col;
    col.r = texture(DiffuseSampler, uv + c * ca).r;
    col.g = texture(DiffuseSampler, uv).g;
    col.b = texture(DiffuseSampler, uv - c * ca).b;

    float lum = dot(col, vec3(0.299, 0.587, 0.114));

    // drained, cold and hard
    vec3 g = mix(col, vec3(lum), (0.38 + 0.2 * Fog) * s);
    g *= mix(vec3(1.0), vec3(0.90, 0.97, 1.06), s);
    vec3 sharp = g * g * (3.0 - 2.0 * g);
    g = mix(g, sharp, 0.3 * s);
    g = max(g - 0.015 * s, 0.0);

    // blood moon: the whole world bleeds
    vec3 red = vec3(lum * 1.35 + 0.035, lum * 0.32, lum * 0.30);
    g = mix(g, red, 0.6 * Blood * s);

    // vignette, tighter with fear, pulsing with the heart
    float vr = r2 * (2.0 + 1.6 * Fear + 0.5 * Blood) * (1.0 + 0.08 * beat);
    float vig = 1.0 - smoothstep(0.18, 0.95, vr);
    g *= mix(1.0, vig, (0.5 + 0.4 * Fear) * min(s, 1.0));
    g += vec3(0.10, 0.0, 0.0) * Blood * (1.0 - vig) * s;

    // film grain
    float n = hash(floor(texCoord * InSize) + floor(Clock * 24.0) * 7.31) - 0.5;
    g += n * (0.03 + 0.05 * Fear) * s;

    fragColor = vec4(clamp(g, 0.0, 1.0), 1.0);
}
