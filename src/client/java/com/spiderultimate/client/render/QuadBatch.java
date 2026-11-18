package com.spiderultimate.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Collects coloured, translucent quads during the world render event and draws them with one draw call.
 * The drawing code follows the Fabric documentation example for custom render pipelines (1.21.11).
 */
public final class QuadBatch {
	private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
		.withLocation(Identifier.fromNamespaceAndPath("spiderultimate", "pipeline/web_quads"))
		.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
		.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	private BufferBuilder buffer;
	private MappableRingBuffer vertexBuffer;
	private int vertexCount;

	/** Adds one quad (four corners in order) as seen from both sides. */
	public void quad(Matrix4fc m,
					 float x1, float y1, float z1, float x2, float y2, float z2,
					 float x3, float y3, float z3, float x4, float y4, float z4,
					 float r, float g, float b, float a) {
		if (buffer == null) {
			buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
		}
		buffer.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		buffer.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
		buffer.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
		buffer.addVertex(m, x4, y4, z4).setColor(r, g, b, a);
		buffer.addVertex(m, x4, y4, z4).setColor(r, g, b, a);
		buffer.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
		buffer.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
		buffer.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		vertexCount += 8;
	}

	public boolean isEmpty() {
		return vertexCount == 0;
	}

	/** Draws everything that was added since the last flush. */
	public void flush(Minecraft client) {
		if (buffer == null || vertexCount == 0) {
			buffer = null;
			vertexCount = 0;
			return;
		}
		MeshData built = buffer.buildOrThrow();
		buffer = null;
		vertexCount = 0;
		MeshData.DrawState drawState = built.drawState();
		VertexFormat format = drawState.format();

		int size = drawState.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) {
				vertexBuffer.close();
			}
			vertexBuffer = new MappableRingBuffer(() -> "spiderultimate web quads", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
		}
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView view = encoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(built.vertexBuffer(), view.data());
		}
		GpuBuffer vertices = vertexBuffer.currentBuffer();

		built.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
		GpuBuffer indices = PIPELINE.getVertexFormat().uploadImmediateIndexBuffer(built.indexBuffer());
		VertexFormat.IndexType indexType = built.drawState().indexType();

		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
			.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "spiderultimate web quads", client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
			client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(PIPELINE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indices, indexType);
			pass.drawIndexed(0, 0, drawState.indexCount(), 1);
		}
		built.close();
		vertexBuffer.rotate();
	}
}
