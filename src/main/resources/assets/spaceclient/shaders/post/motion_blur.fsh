#version 330
#extension GL_ARB_separate_shader_objects : require

// Motion blur by accumulation: each frame is mixed with what was shown the
// frame before, so anything that moves across the screen leaves a short,
// fading trail. The history starts out transparent (its clear colour), and a
// transparent history pixel means "nothing yet" - the current frame is used
// as is, so switching the blur on never fades in from black.

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(std140) uniform MotionBlurConfig {
    float BlendFactor;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 current = texture(InSampler, texCoord);
    vec4 previous = texture(PrevSampler, texCoord);
    float keep = previous.a > 0.5 ? BlendFactor : 0.0;
    fragColor = vec4(mix(current.rgb, previous.rgb, keep), 1.0);
}
