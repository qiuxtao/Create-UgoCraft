package com.qiuxtao.create_ugocraft.collision;

import com.qiuxtao.create_ugocraft.collision.ContinuousOBBCollider.ContinuousSeparationManifold;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StructureCollider {

	public static void collideEntities(Entity structureEntity, Map<BlockPos, BlockState> blocks, Vec3 structurePos, Matrix3d rotation, Vec3 structureMotion) {
		Level level = structureEntity.level();
		AABB expandedBounds = structureEntity.getBoundingBox().inflate(2.0 + structureMotion.length());

		List<Entity> entities = level.getEntities(structureEntity, expandedBounds, e -> !e.noPhysics && !e.isSpectator());

		for (Entity entity : entities) {
			if (entity.getVehicle() != null) continue; // Skip passengers

			AABB entityBounds = entity.getBoundingBox();
			if (entity.level().isClientSide && entityBounds.getYsize() > 1) {
				entityBounds = entityBounds.contract(0, 2 / 16f, 0); // Shrink player on client to avoid getting stuck
			}

			// Localize entity position relative to the structure core
			Vec3 entityPos = entity.position().subtract(structurePos);
			// But wait, structureEntity.position() might be the actual animated center.
			// Let's assume the caller passes the correct coordinate frame where origin = corePos for blocks.
			// We need the entity relative to the moving structure origin.
			
			// Transform entity motion
			Vec3 entityMotion = entity.getDeltaMovement();
			Vec3 relativeMotion = entityMotion.subtract(structureMotion);
			
			Matrix3d inverseRotation = rotation.copy().transpose();
			relativeMotion = inverseRotation.transform(relativeMotion);

			Vec3 localCenter = inverseRotation.transform(entityPos);
			AABB localBB = entityBounds.move(-entity.getX(), -entity.getY(), -entity.getZ())
					.move(localCenter)
					.inflate(1.0E-7D);

			OrientedBB obb = new OrientedBB(localBB);
			obb.setRotation(rotation);

			Vec3 collisionResponse = Vec3.ZERO;
			Vec3 normal = Vec3.ZERO;
			boolean surfaceCollision = false;
			double temporalResponse = 1;

			// Check against each block in the structure
			for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
				BlockPos pos = entry.getKey();
				VoxelShape shape = entry.getValue().getShape(level, pos);
				if (shape.isEmpty()) continue;

				List<AABB> blockAABBs = shape.toAabbs();
				for (AABB blockAABB : blockAABBs) {
					AABB bb = blockAABB.move(pos); // Local space AABB

					// Fast rejection
					Vec3 currentCenter = obb.getCenter().add(collisionResponse);
					if (Math.abs(currentCenter.x - bb.getCenter().x) - entityBounds.getXsize() - 1 > bb.getXsize() / 2) continue;
					if (Math.abs((currentCenter.y + relativeMotion.y) - bb.getCenter().y) - entityBounds.getYsize() - 1 > bb.getYsize() / 2) continue;
					if (Math.abs(currentCenter.z - bb.getCenter().z) - entityBounds.getZsize() - 1 > bb.getZsize() / 2) continue;

					obb.setCenter(currentCenter);
					ContinuousSeparationManifold intersect = obb.intersect(bb, relativeMotion);
					if (intersect == null) continue;

					surfaceCollision |= intersect.isSurfaceCollision();
					double timeOfImpact = intersect.getTimeOfImpact();
					boolean isTemporal = timeOfImpact > 0 && timeOfImpact < 1;

					if (!isTemporal) {
						Vec3 separation = intersect.asSeparationVec(entity.maxUpStep());
						if (separation != null && !separation.equals(Vec3.ZERO)) {
							collisionResponse = collisionResponse.add(separation);
						}
					} else {
						if (temporalResponse > timeOfImpact) {
							temporalResponse = timeOfImpact;
						}
					}

					Vec3 collidingNormal = intersect.getCollisionNormal();
					if (collidingNormal != null && timeOfImpact >= 0 && temporalResponse >= timeOfImpact) {
						normal = collidingNormal;
					}
				}
			}

			// Resolve collision
			boolean hardCollision = !collisionResponse.equals(Vec3.ZERO);
			boolean temporalCollision = temporalResponse != 1;
			
			Vec3 motionResponse = !temporalCollision ? relativeMotion : relativeMotion.normalize().scale(relativeMotion.length() * temporalResponse);
			
			motionResponse = rotation.transform(motionResponse).add(structureMotion);
			collisionResponse = rotation.transform(collisionResponse);
			normal = rotation.transform(normal).normalize();

			if (temporalCollision) {
				if (motionResponse.y != entityMotion.y) {
					entity.setDeltaMovement(entityMotion.multiply(1, 0, 1).add(0, motionResponse.y, 0));
					entityMotion = entity.getDeltaMovement();
				}
			}

			if (hardCollision) {
				double horizonalEpsilon = 1 / 128f;
				if (entityMotion.x() != 0 && Math.abs(collisionResponse.x()) > horizonalEpsilon && entityMotion.x() > 0 == collisionResponse.x() < 0)
					entityMotion = entityMotion.multiply(0, 1, 1);
				if (entityMotion.y() != 0 && collisionResponse.y() != 0 && entityMotion.y() > 0 == collisionResponse.y() < 0)
					entityMotion = entityMotion.multiply(1, 0, 1).add(0, structureMotion.y, 0);
				if (entityMotion.z() != 0 && Math.abs(collisionResponse.z()) > horizonalEpsilon && entityMotion.z() > 0 == collisionResponse.z() < 0)
					entityMotion = entityMotion.multiply(1, 1, 0);
			}

			if (!hardCollision && !surfaceCollision) continue;

			entity.setPos(entity.getX() + collisionResponse.x(), entity.getY() + collisionResponse.y(), entity.getZ() + collisionResponse.z());

			if (surfaceCollision) {
				entity.fallDistance = 0;
				entity.setOnGround(true);
				if (entity instanceof ItemEntity) {
					entityMotion = entityMotion.multiply(0.5, 1, 0.5);
				}
				
				// Push entity along with the vehicle
				Vec3 contactPointMotion = calculateContactPointMotion(entity.position(), structurePos, rotation, structureMotion);
				entity.setPos(entity.getX() + contactPointMotion.x(), entity.getY() + contactPointMotion.y(), entity.getZ() + contactPointMotion.z());
			}

			entity.setDeltaMovement(entityMotion);
		}
	}

	private static Vec3 calculateContactPointMotion(Vec3 contactPoint, Vec3 structurePos, Matrix3d rotation, Vec3 structureMotion) {
		Vec3 relative = contactPoint.subtract(structurePos);
		Vec3 rotated = rotation.transform(relative);
		Vec3 currentGlobal = structurePos.add(rotated).add(structureMotion);
		Vec3 oldGlobal = structurePos.add(relative);
		return currentGlobal.subtract(oldGlobal);
	}
}
