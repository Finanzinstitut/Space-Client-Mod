#version 330
#extension GL_ARB_separate_shader_objects : require

// Pulls every pixel towards or away from its own brightness. Written in the
// shape 26.3's post shaders take - explicit locations and uniform blocks -
// because the game now compiles them for Vulkan as well as OpenGL.

uniform sampler2D InSampler;

layout(std140) uniform SaturationConfig {
    float SaturationAmount;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const vec3 Gray = vec3(0.3, 0.59, 0.11);

void main() {
    vec4 InTexel = texture(InSampler, texCoord);
    float Luma = dot(InTexel.rgb, Gray);
    vec3 Chroma = InTexel.rgb - Luma;
    fragColor = vec4((Chroma * SaturationAmount) + Luma, 1.0);
}
