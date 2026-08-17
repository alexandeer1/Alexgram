#extension GL_OES_EGL_image_external : require

precision lowp float;

varying vec2 uv;
varying vec2 auv;
uniform samplerExternalOES sTexture;
uniform vec2 pixelWH;
uniform float roundRadius;
uniform float scale;
uniform float alpha;
uniform float shapeFrom, shapeTo, shapeT;
uniform float dual;
uniform float blur;

uniform int filterType;
uniform float filterIntensity;
uniform float filterTime;

float modI(float a,float b) {
  return floor(a-floor((a+0.5)/b)*b+0.5);
}
bool eq(float a, float b) {
  return abs(a - b) < .1;
}
bool eq(float a, float b, float b2) {
  return abs(a - b) < .1 || abs(a - b2) < .1;
}
float box(vec2 position, vec2 halfSize, float cornerRadius) {
  position = abs(position) - halfSize + cornerRadius;
  return length(max(position, 0.0)) + min(max(position.x, position.y), 0.0) - cornerRadius;
}
float star(in vec2 p, in float r) {
  const vec2  acs = vec2(.9659258, .258819);
  const vec2  ecs = vec2(.8090169, .5877852);
  float bn = mod(atan(p.x,p.y),.52359876)-.26179938;
  p = length(p)*vec2(cos(bn),abs(sin(bn))) - r*acs;
  p += ecs*clamp( -dot(p,ecs), 0.0, r*acs.y/ecs.y);
  return length(p)*sign(p.x);
}
float opSmoothUnion(float d1, float d2, float k) {
  float h = max(k-abs(d1-d2),0.0);
  return min(d1, d2) - h*h*0.25/k;
}
float scene() {
  vec2 p = (auv - vec2(.5)) * vec2(1., pixelWH.x / pixelWH.y);
  vec2 r = .5 * vec2(1., pixelWH.x / pixelWH.y) * scale;
  float R = min(r.x, r.y), rr = roundRadius / pixelWH.y;
  float a = modI(shapeFrom, 3.), b = modI(shapeTo, 3.);
  float boxSDF = box(
    p,
    mix(eq(a, 2.)     ? r : vec2(R), eq(b, 2.)     ? r : vec2(R), shapeT),
    mix(eq(a, 0., 3.) ? R : rr,      eq(b, 0., 3.) ? R : rr,      shapeT)
  ) * pixelWH.x;
  if (eq(3., a, b)) {
    float starSDF = opSmoothUnion(box(p, vec2(R * .78), R), star(p, R * .78), .25) * pixelWH.x;
    float starA = eq(a, 3.) ? 1. - shapeT : 0.;
    float starB = eq(b, 3.) ? shapeT : 0.;
    return mix(boxSDF, starSDF, starA + starB);
  } else {
    return boxSDF;
  }
}
vec4 makeblur() {
  vec2 S = 4. * vec2(1., pixelWH.x / pixelWH.y);
  vec2 st = fract(uv * S);
  st=st*st*(3.0-2.0*st);
  vec2 u = floor(uv * S) / S;
  return mix(
    mix(texture2D(sTexture, u + vec2(0., 0.) / S), texture2D(sTexture, u + vec2(1., 0.) / S), st.x),
    mix(texture2D(sTexture, u + vec2(0., 1.) / S), texture2D(sTexture, u + vec2(1., 1.) / S), st.x),
    st.y
  );
}

vec4 applySnapchatFilter(vec2 texCoord, vec4 baseColor) {
  if (filterType == 0 || filterIntensity <= 0.0) {
    return baseColor;
  }

  vec3 col = baseColor.rgb;
  vec3 outCol = col;
  float intensity = clamp(filterIntensity, 0.0, 1.0);

  if (filterType == 1) {
    // 1. BEAUTY
    outCol = vec3(col.r * 1.08 + 0.04, col.g * 1.04 + 0.02, col.b * 1.00 + 0.01);
  } else if (filterType == 2) {
    // 2. GOLDEN HOUR
    vec3 golden = vec3(col.r * 1.22 + 0.07, col.g * 1.08 + 0.03, col.b * 0.78 - 0.04);
    float vig = 1.0 - smoothstep(0.40, 0.85, length(texCoord - vec2(0.5)));
    outCol = golden * (0.82 + 0.18 * vig);
  } else if (filterType == 3) {
    // 3. VINTAGE 90S
    vec3 vintage = vec3(col.r * 1.15 + 0.09, col.g * 1.05 + 0.07, col.b * 0.88 + 0.10);
    float vig = 1.0 - smoothstep(0.35, 0.80, length(texCoord - vec2(0.5)));
    outCol = vintage * (0.75 + 0.25 * vig);
  } else if (filterType == 4) {
    // 4. CYBERPUNK
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 cyan = vec3(0.0, 0.9 * (lum * 1.3), 1.0 * (lum * 1.3));
    vec3 magenta = vec3(1.0 * (0.3 + lum * 0.9), 0.05 * (0.3 + lum * 0.9), 0.7 * (0.3 + lum * 0.9));
    outCol = mix(cyan, magenta, lum);
  } else if (filterType == 5) {
    // 5. BW NOIR
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    lum = smoothstep(0.10, 0.90, lum);
    float vig = 1.0 - smoothstep(0.25, 0.75, length(texCoord - vec2(0.5)));
    outCol = vec3(lum * (0.65 + 0.35 * vig));
  } else if (filterType == 6) {
    // 6. PASTEL ANIME
    vec3 pastel = vec3(col.r * 1.10 + 0.06, col.g * 1.15 + 0.05, col.b * 1.25 + 0.08);
    outCol = mix(vec3(dot(pastel, vec3(0.333))), pastel, 1.25);
  } else if (filterType == 7) {
    // 7. VHS GLITCH
    float shift = 0.012 * intensity;
    float r = texture2D(sTexture, texCoord - vec2(shift, 0.0)).r;
    float g = texture2D(sTexture, texCoord).g;
    float b = texture2D(sTexture, texCoord + vec2(shift, 0.0)).b;
    float scanline = sin(texCoord.y * pixelWH.y * 1.5) * 0.06;
    outCol = vec3(r, g, b) - scanline;
  } else if (filterType == 8) {
    // 8. FISHEYE
    vec2 d = texCoord - vec2(0.5);
    float r = length(d);
    vec2 fishUv = vec2(0.5) + (d / max(r, 0.0001)) * (r + r * r * 0.45 * intensity);
    vec3 fishCol = (fishUv.x >= 0.0 && fishUv.x <= 1.0 && fishUv.y >= 0.0 && fishUv.y <= 1.0) ? texture2D(sTexture, fishUv).rgb : vec3(0.0);
    float fishVig = 1.0 - smoothstep(0.42, 0.52, r);
    outCol = fishCol * fishVig;
  } else if (filterType == 9) {
    // 9. THERMAL
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 thermal = clamp(vec3(sin(lum * 3.14159 - 1.57), sin(lum * 3.14159), cos(lum * 3.14159)), 0.0, 1.0);
    if (lum > 0.75) thermal += vec3((lum - 0.75) * 3.0);
    outCol = thermal;
  } else if (filterType == 10) {
    // 10. SEPIA
    outCol = vec3(dot(col, vec3(0.393, 0.769, 0.189)), dot(col, vec3(0.349, 0.686, 0.168)), dot(col, vec3(0.272, 0.534, 0.131)));
  } else if (filterType == 11) {
    // 11. NIGHT VISION
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    float scope = 1.0 - smoothstep(0.38, 0.50, length(texCoord - vec2(0.5)));
    outCol = vec3(0.10 * lum, (lum * 1.35 + 0.10), 0.15 * lum) * scope;
  } else if (filterType == 12) {
    // 12. COMIC POP ART
    outCol = clamp(floor(col * 4.0 + 0.5) / 4.0 * 1.15, 0.0, 1.0);
  } else if (filterType == 13) {
    // 13. ROSE BLUSH
    outCol = vec3(col.r * 1.20 + 0.08, col.g * 0.95 + 0.03, col.b * 1.10 + 0.06);
  } else if (filterType == 14) {
    // 14. COLD ICE
    outCol = vec3(max(0.0, col.r * 0.85 - 0.04), col.g * 1.05 + 0.03, col.b * 1.35 + 0.11);
  } else if (filterType == 15) {
    // 15. CINEMA TEAL
    vec3 cinema = vec3(col.r * 1.25 + 0.06, col.g * 1.05, col.b * 1.30 + 0.07);
    if (col.r > col.b) cinema.r += 0.08; else cinema.b += 0.10;
    outCol = cinema;
  } else if (filterType == 16) {
    // 16. PORTRA 400
    outCol = clamp(vec3(col.r * 1.12 + 0.04, col.g * 1.05 + 0.02, col.b * 0.92 + 0.05), 0.0, 1.0);
  } else if (filterType == 17) {
    // 17. FUJI VELVIA
    vec3 velvia = vec3(col.r * 1.05, col.g * 1.25 + 0.02, col.b * 1.15 + 0.04);
    float avg = dot(velvia, vec3(0.333));
    outCol = clamp(vec3(avg) + (velvia - vec3(avg)) * 1.35, 0.0, 1.0);
  } else if (filterType == 18) {
    // 18. VAPORWAVE
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 gr = mix(vec3(1.0, 0.15, 0.75), vec3(0.05, 0.85, 1.0), texCoord.y);
    outCol = clamp(gr * (lum * 1.4), 0.0, 1.0);
  } else if (filterType == 19) {
    // 19. DREAMY BLOOM
    outCol = clamp(col * 1.15 + (col * col) * 0.25 + vec3(0.05, 0.04, 0.06), 0.0, 1.0);
  } else if (filterType == 20) {
    // 20. SIN CITY RED
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    float mono = clamp((lum - 0.12) * 1.35, 0.0, 1.0);
    float redDom = col.r - max(col.g, col.b);
    if (redDom > 0.15 && col.r > 0.35) {
      outCol = vec3(min(1.0, col.r * 1.25 + 0.08), col.g * 0.35, col.b * 0.35);
    } else {
      outCol = vec3(mono);
    }
  } else if (filterType == 21) {
    // 21. EMERALD FOREST
    outCol = clamp(vec3(col.r * 0.85 - 0.02, col.g * 1.18 + 0.05, col.b * 0.95 + 0.02), 0.0, 1.0);
  } else if (filterType == 22) {
    // 22. SUNSET PEACH
    outCol = clamp(vec3(col.r * 1.18 + 0.08, col.g * 1.02 + 0.04, col.b * 0.90 + 0.02), 0.0, 1.0);
  } else if (filterType == 23) {
    // 23. PRISM REFRACTION
    float diag = (texCoord.x + texCoord.y) * 0.5;
    vec3 prism = vec3(0.5 + 0.5 * cos(diag * 6.28318), 0.5 + 0.5 * cos((diag + 0.33) * 6.28318), 0.5 + 0.5 * cos((diag + 0.67) * 6.28318));
    outCol = clamp(col * 0.85 + prism * 0.25, 0.0, 1.0);
  } else if (filterType == 24) {
    // 24. MOCHA WARM
    outCol = clamp(vec3(col.r * 1.15 + 0.06, col.g * 0.98 + 0.03, col.b * 0.82 + 0.01), 0.0, 1.0);
  } else if (filterType == 25) {
    // 25. DUOTONE NEON
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 duo = mix(vec3(0.30, 0.0, 0.80), vec3(1.0, 0.80, 0.10), lum);
    outCol = clamp(duo * (lum * 1.4), 0.0, 1.0);
  } else if (filterType == 26) {
    // 26. HOLOGRAPHIC
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    float wave = sin((texCoord.x * 4.0 + texCoord.y * 4.0 + lum * 5.0) * 3.14159);
    vec3 holo = vec3(0.5 + 0.5 * sin(wave * 3.14159), 0.5 + 0.5 * sin(wave * 3.14159 + 2.094), 0.5 + 0.5 * sin(wave * 3.14159 + 4.188));
    outCol = clamp(col * 0.70 + holo * 0.45, 0.0, 1.0);
  } else if (filterType == 27) {
    // 27. ACID POP
    vec3 acid = col * 1.35;
    float avg = dot(acid, vec3(0.333));
    outCol = clamp(vec3(avg) + (acid - vec3(avg)) * 1.60, 0.0, 1.0);
  } else if (filterType == 28) {
    // 28. BLADE RUNNER 2049
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 amber = vec3(lum * 1.35 + 0.15, lum * 0.85 + 0.05, (1.0 - lum) * 0.45 + lum * 0.20);
    outCol = clamp(amber, 0.0, 1.0);
  } else if (filterType == 29) {
    // 29. WES ANDERSON
    outCol = clamp(vec3(col.r * 1.18 + 0.06, col.g * 1.12 + 0.04, col.b * 0.82 + 0.01), 0.0, 1.0);
  } else if (filterType == 30) {
    // 30. KODAK GOLD 200
    outCol = clamp(vec3(col.r * 1.15 + 0.05, col.g * 1.06 + 0.03, col.b * 0.88 + 0.01), 0.0, 1.0);
  } else if (filterType == 31) {
    // 31. ILFORD HP5
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    float mono = clamp((lum - 0.5) * 1.45 + 0.5, 0.0, 1.0);
    outCol = vec3(mono);
  } else if (filterType == 32) {
    // 32. SOFT AURA
    outCol = clamp(col * 1.12 + vec3(0.05, 0.04, 0.08), 0.0, 1.0);
  } else if (filterType == 33) {
    // 33. PEACH GLOW
    outCol = clamp(vec3(col.r * 1.16 + 0.07, col.g * 1.04 + 0.03, col.b * 0.98 + 0.02), 0.0, 1.0);
  } else if (filterType == 34) {
    // 34. HONEY WARMTH
    outCol = clamp(vec3(col.r * 1.20 + 0.06, col.g * 1.08 + 0.04, col.b * 0.78), 0.0, 1.0);
  } else if (filterType == 35) {
    // 35. LAVENDER HAZE
    outCol = clamp(vec3(col.r * 1.10 + 0.06, col.g * 0.92 + 0.01, col.b * 1.22 + 0.09), 0.0, 1.0);
  } else if (filterType == 36) {
    // 36. SUPER 8
    outCol = clamp(vec3(col.r * 1.22 + 0.07, col.g * 1.05 + 0.03, col.b * 0.72 + 0.01), 0.0, 1.0);
  } else if (filterType == 37) {
    // 37. POLAROID 600
    outCol = clamp(vec3(col.r * 0.95 + 0.08, col.g * 1.02 + 0.06, col.b * 0.92 + 0.08), 0.0, 1.0);
  } else if (filterType == 38) {
    // 38. RETRO DISCO
    outCol = clamp(vec3(col.r * 1.25 + 0.06, col.g * 0.90 + 0.02, col.b * 1.30 + 0.10), 0.0, 1.0);
  } else if (filterType == 39) {
    // 39. DARK ACADEMIA
    float lum = dot(col, vec3(0.35, 0.55, 0.10));
    outCol = clamp(vec3(lum * 1.05 + 0.05, lum * 0.88 + 0.03, lum * 0.65 + 0.01), 0.0, 1.0);
  } else if (filterType == 40) {
    // 40. RETRO SCANLINES
    float scan = (fract(texCoord.y * 360.0) < 0.5) ? 0.80 : 1.15;
    outCol = clamp(col * scan * vec3(1.05, 1.12, 1.02), 0.0, 1.0);
  } else if (filterType == 41) {
    // 41. VORTEX WARP
    vec2 tc = texCoord - vec2(0.5);
    float r = length(tc);
    if (r < 0.5) {
      float angle = atan(tc.y, tc.x);
      float twist = (1.0 - r / 0.5) * 2.5 * intensity;
      vec2 newUv = vec2(0.5) + vec2(cos(angle + twist), sin(angle + twist)) * r;
      outCol = texture2D(sTexture, newUv).rgb;
    }
  } else if (filterType == 42) {
    // 42. KALEIDOSCOPE
    vec2 tc = texCoord - vec2(0.5);
    float r = length(tc);
    float a = atan(tc.y, tc.x);
    if (a < 0.0) a += 6.2831853;
    float seg = 1.04719755; // 6-fold
    float modA = mod(a, seg);
    if (modA > seg * 0.5) modA = seg - modA;
    vec2 kUv = vec2(0.5) + vec2(cos(modA), sin(modA)) * r;
    outCol = texture2D(sTexture, kUv).rgb;
  } else if (filterType == 43) {
    // 43. INFRARED AEROCHROME
    float greenDiff = max(0.0, col.g - (col.r + col.b) * 0.45);
    outCol = clamp(vec3(col.r * 0.85 + greenDiff * 1.85 + 0.10, col.g * 0.35 + 0.04, col.b * 0.75 + greenDiff * 1.15 + 0.06), 0.0, 1.0);
  } else if (filterType == 44) {
    // 44. NEON CYAN MAGENTA
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 cyan = vec3(0.0, 0.90, 1.0);
    vec3 magenta = vec3(0.85, 0.0, 0.70);
    outCol = clamp(mix(magenta, cyan, lum) * (lum * 1.3), 0.0, 1.0);
  } else if (filterType == 45) {
    // 45. GOLD DUST
    outCol = clamp(vec3(col.r * 1.22 + 0.08, col.g * 1.10 + 0.05, col.b * 0.70 + 0.01), 0.0, 1.0);
  } else if (filterType == 46) {
    // 46. ANAMORPHIC FLARE
    float streak = pow(dot(col, vec3(0.333)), 3.0) * 0.45;
    outCol = clamp(col * vec3(0.85, 0.95, 1.25) + vec3(0.02, 0.04 + streak * 0.4, 0.10 + streak * 0.9), 0.0, 1.0);
  } else if (filterType == 47) {
    // 47. EDGE NEON GLOW
    vec3 cR = texture2D(sTexture, texCoord + vec2(0.003, 0.0)).rgb;
    vec3 cD = texture2D(sTexture, texCoord + vec2(0.0, 0.003)).rgb;
    float edge = length(col - cR) + length(col - cD);
    outCol = clamp(col * 0.25 + vec3(edge * 1.8, edge * 0.4, edge * 1.5), 0.0, 1.0);
  } else if (filterType == 48) {
    // 48. RADIOACTIVE GLOW
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    outCol = clamp(vec3(lum * 0.45, lum * 1.45 + 0.15, lum * 0.15), 0.0, 1.0);
  } else if (filterType == 49) {
    // 49. SOLAR ECLIPSE
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    float solar = abs(sin(lum * 3.14159));
    outCol = clamp(vec3(solar * 1.1, solar * 0.75, (1.0 - solar) * 0.35), 0.0, 1.0);
  } else if (filterType == 50) {
    // 50. PIXEL ART 8-BIT
    vec2 pCoord = floor(texCoord * 64.0) / 64.0;
    vec3 pCol = texture2D(sTexture, pCoord).rgb;
    vec3 qCol = floor(pCol * 4.0) / 3.0;
    outCol = clamp(qCol, 0.0, 1.0);
  } else if (filterType == 51) {
    // 51. DOUBLE EXPOSURE
    vec3 ghost = texture2D(sTexture, texCoord + vec2(0.02 * intensity, 0.0)).rgb;
    outCol = clamp(col * 0.7 + ghost.bgr * 0.6, 0.0, 1.0);
  } else if (filterType == 52) {
    // 52. MAGMA VOLCANO
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    outCol = clamp(vec3(lum * 1.35 + 0.08, lum * 0.55, (1.0 - lum) * 0.12), 0.0, 1.0);
  } else if (filterType == 53) {
    // 53. DEEP ABYSS OCEAN
    outCol = clamp(vec3(col.r * 0.40, col.g * 1.15 + 0.04, col.b * 1.35 + 0.14), 0.0, 1.0);
  } else if (filterType == 54) {
    // 54. GLITCH DATAMOSH
    float blockY = floor(texCoord.y * 32.0);
    float shift = (mod(blockY, 5.0) == 1.0) ? 0.03 * intensity : ((mod(blockY, 7.0) == 3.0) ? -0.02 * intensity : 0.0);
    outCol = texture2D(sTexture, texCoord + vec2(shift, 0.0)).rgb;
  } else if (filterType == 55) {
    // 55. STARLIGHT SPARKLE
    float b = dot(col, vec3(0.333));
    float star = (b > 0.72) ? pow((b - 0.72) / 0.28, 2.0) * 0.35 : 0.0;
    outCol = clamp(col * 1.05 + vec3(star, star * 0.9, star * 1.2), 0.0, 1.0);
  } else if (filterType == 56) {
    // 56. CHRONO SPEED BLUR
    vec2 tc = texCoord - vec2(0.5);
    vec3 sum = vec3(0.0);
    for (int s = 0; s < 4; s++) {
      float f = 1.0 - float(s) * 0.03 * intensity;
      sum += texture2D(sTexture, vec2(0.5) + tc * f).rgb;
    }
    outCol = sum * 0.25;
  } else if (filterType == 57) {
    // 57. MIDNIGHT PURPLE
    outCol = clamp(vec3(col.r * 0.90 + 0.10, col.g * 0.45 + 0.02, col.b * 1.35 + 0.18), 0.0, 1.0);
  } else if (filterType == 58) {
    // 58. RIPPLE WATER DROPS
    vec2 tc = texCoord - vec2(0.5);
    float dist = length(tc);
    float ripple = sin(dist * 28.0 - (filterTime * 5.0)) * 0.025 * intensity;
    vec2 ripUv = texCoord + (tc / max(dist, 0.001)) * ripple;
    outCol = texture2D(sTexture, ripUv).rgb * vec3(0.88, 1.05, 1.25);
  } else if (filterType == 59) {
    // 59. PINCH BULGE LENS
    vec2 tc = texCoord - vec2(0.5);
    float dist = length(tc);
    if (dist < 0.42) {
      float p = dist / 0.42;
      float bFactor = pow(p, 0.5 + 0.5 * (1.0 - intensity));
      vec2 bulgeUv = vec2(0.5) + tc * (bFactor / max(p, 0.001));
      outCol = texture2D(sTexture, bulgeUv).rgb;
    }
  } else if (filterType == 60) {
    // 60. RETRO GAMEBOY
    vec2 pCoord = floor(texCoord * 72.0) / 72.0;
    vec3 pCol = texture2D(sTexture, pCoord).rgb;
    float lum = dot(pCol, vec3(0.299, 0.587, 0.114));
    if (lum < 0.25) outCol = vec3(0.06, 0.22, 0.06);
    else if (lum < 0.50) outCol = vec3(0.19, 0.38, 0.19);
    else if (lum < 0.75) outCol = vec3(0.54, 0.67, 0.06);
    else outCol = vec3(0.61, 0.74, 0.06);
  } else if (filterType == 61) {
    // 61. CYBER MATRIX RAIN
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    float rain = (mod(floor(texCoord.x * 36.0) + floor(texCoord.y * 18.0 + (filterTime * 12.0)), 7.0) == 0.0) ? 1.5 : 0.8;
    outCol = clamp(vec3(lum * 0.1, lum * 1.35 * rain + 0.12, lum * 0.18), 0.0, 1.0);
  } else if (filterType == 62) {
    // 62. NEON WIREFRAME GRID
    float isGrid = (texCoord.y > 0.55 && (mod(texCoord.y * 30.0, 1.0) < 0.1 || mod(abs(texCoord.x - 0.5) / (texCoord.y - 0.5) * 6.0, 1.0) < 0.15)) ? 1.0 : 0.0;
    outCol = clamp(col * vec3(1.15, 0.70, 1.30) + vec3(isGrid * 0.85, isGrid * 0.15, isGrid * 1.0), 0.0, 1.0);
  } else if (filterType == 63) {
    // 63. MIRROR QUAD SPLIT
    vec2 qUv = abs(texCoord - vec2(0.5));
    outCol = texture2D(sTexture, qUv * 2.0).rgb;
  } else if (filterType == 64) {
    // 64. TUNNEL ZOOM WARP
    vec2 tc = abs(texCoord - vec2(0.5));
    float maxD = max(tc.x, tc.y);
    float modD = mod(maxD, 0.12) / 0.12;
    vec2 tUv = vec2(0.5) + (texCoord - vec2(0.5)) * (0.4 + modD * 0.6);
    outCol = texture2D(sTexture, tUv).rgb * vec3(1.1, 0.85, 1.3);
  } else if (filterType == 65) {
    // 65. HOLOGRAM BLUE GLITCH
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    float scan = (mod(texCoord.y * 240.0, 1.0) < 0.5) ? 0.75 : 1.25;
    outCol = clamp(vec3(lum * 0.15 * scan, lum * 0.90 * scan + 0.05, lum * 1.45 * scan + 0.20), 0.0, 1.0);
  } else if (filterType == 66) {
    // 66. HEATWAVE MIRAGE
    float waveX = sin(texCoord.y * 35.0 + (filterTime * 8.0)) * 0.015 * intensity;
    outCol = texture2D(sTexture, texCoord + vec2(waveX, 0.0)).rgb * vec3(1.18, 1.05, 0.85);
  } else if (filterType == 67) {
    // 67. CROSS HATCH SKETCH
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    float ink = 1.0;
    vec2 px = texCoord * 200.0;
    if (lum < 0.80 && mod(px.x + px.y, 4.0) < 1.0) ink -= 0.35;
    if (lum < 0.55 && mod(px.x - px.y, 4.0) < 1.0) ink -= 0.35;
    if (lum < 0.30 && mod(px.x, 3.0) < 1.0) ink -= 0.35;
    outCol = vec3(clamp(ink, 0.0, 1.0));
  } else if (filterType == 68) {
    // 68. 1920 SILENT CINEMA
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    float flicker = 0.90 + 0.10 * sin(filterTime * 30.0);
    vec3 sep = vec3(lum * 1.15 + 0.10, lum * 0.95 + 0.05, lum * 0.65) * flicker;
    float scratch = (mod(texCoord.x * 50.0 + (filterTime * 2.0), 1.0) < 0.02) ? 0.3 : 0.0;
    outCol = clamp(sep + vec3(scratch), 0.0, 1.0);
  } else if (filterType == 69) {
    // 69. CHROMATIC SPHERE
    vec2 tc = texCoord - vec2(0.5);
    float dist = length(tc);
    if (dist < 0.40) {
      float p = dist / 0.40;
      float invP = sin(p * 1.57079);
      vec2 orbUv = vec2(0.5) - tc * invP;
      vec3 orbCol = texture2D(sTexture, orbUv).rgb;
      float rim = (p > 0.85) ? (p - 0.85) / 0.15 * 0.4 : 0.0;
      outCol = clamp(orbCol * vec3(0.95, 0.85, 1.25) + vec3(rim, 0.0, rim * 1.5), 0.0, 1.0);
    } else {
      outCol = col * 0.35;
    }
  }

  return vec4(mix(col, outCol, intensity), baseColor.a);
}

vec4 program() {
  if (scale <= 0.)
    return vec4(0.);
  float dalpha = clamp(1. - scene(), 0., 2.) / 2. * alpha;
  if (dalpha <= 0.)
    return vec4(0.);

  vec4 baseCol;
  if (blur >= 1.)
    baseCol = makeblur();
  else if (blur <= 0.)
    baseCol = texture2D(sTexture, uv) * vec4(1., 1., 1., dalpha);
  else
    baseCol = mix(texture2D(sTexture, uv), makeblur(), blur) * vec4(1., 1., 1., dalpha);

  return applySnapchatFilter(uv, baseCol);
}

void main() {
  gl_FragColor = dual < .5 ? applySnapchatFilter(uv, texture2D(sTexture, uv)) : program();
}