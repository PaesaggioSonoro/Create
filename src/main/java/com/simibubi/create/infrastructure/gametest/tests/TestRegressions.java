package com.simibubi.create.infrastructure.gametest.tests;

import static com.simibubi.create.infrastructure.gametest.CreateGameTestHelper.TEN_SECONDS;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.foundation.collision.CollisionList;
import com.simibubi.create.foundation.collision.ContinuousOBBCollider;
import com.simibubi.create.foundation.collision.ContinuousOBBCollider.CollisionResponse;
import com.simibubi.create.foundation.collision.Matrix3d;
import com.simibubi.create.foundation.collision.OrientedBB;
import com.simibubi.create.infrastructure.gametest.CreateGameTestHelper;
import com.simibubi.create.infrastructure.gametest.GameTestGroup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@GameTestGroup(path = "regressions")
public class TestRegressions {
	@GameTest(template = "issue9615_efficient_deployers", timeoutTicks = TEN_SECONDS)
	public static void issue9615_efficientDeployers(CreateGameTestHelper helper) {
		final BlockPos lever = new BlockPos(2, 5, 0);
		final BlockPos goal = new BlockPos(1, 3, 4);
		helper.unpowerLever(lever);
		helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.LIME_STAINED_GLASS, goal));
	}

	@GameTest(template = "issue10218_collision", batch = "regression_collision")
	public static void issue10218_centeredItemCollision(CreateGameTestHelper helper) {
		CollisionList colliders = new CollisionList();
		new CollisionList.Populate(colliders).consume(0, 0, 0, 1, 1, 1);
		CollisionList scratch = new CollisionList();
		ItemEntity item = new ItemEntity(helper.getLevel(), .5, .375, .5,
			new ItemStack(Items.OAK_SAPLING), 0, 0, 0);
		OrientedBB obb = new OrientedBB(item.getBoundingBox().inflate(1E-7));
		helper.assertTrue(obb.getCenter().equals(new Vec3(.5, .5, .5)), "Item AABB must be exactly centered");

		for (Direction.Axis axis : Direction.Axis.values()) {
			for (float angle : new float[] {0, 15, 45, 90}) {
				float radians = (float) Math.toRadians(angle);
				Matrix3d rotation = switch (axis) {
					case X -> new Matrix3d().asXRotation(radians);
					case Y -> new Matrix3d().asYRotation(radians);
					case Z -> new Matrix3d().asZRotation(radians);
				};
				obb.setRotation(rotation);
				for (float maxStep : new float[] {item.maxUpStep(), 1}) {
					for (boolean horizontalPass : new boolean[] {false, true}) {
						CollisionResponse result = ContinuousOBBCollider.collideMany(colliders, scratch, obb,
							Vec3.ZERO, maxStep, horizontalPass);
						assertFiniteCollision(helper, result);
						helper.assertTrue(scratch.size == 1, "Centered item must reach the collision solver");
					}
				}
			}
		}
		helper.succeed();
	}

	@GameTest(template = "issue10218_collision", batch = "regression_collision")
	public static void issue10218_offsetItemCollision(CreateGameTestHelper helper) {
		CollisionList colliders = new CollisionList();
		new CollisionList.Populate(colliders).consume(0, 0, 0, 1, 1, 1);
		CollisionList scratch = new CollisionList();
		ItemEntity item = new ItemEntity(helper.getLevel(), .5, .375, .5,
			new ItemStack(Items.OAK_SAPLING), 0, 0, 0);
		OrientedBB obb = new OrientedBB(item.getBoundingBox().inflate(1E-7));

		for (Direction direction : Direction.values()) {
			Vec3 offset = Vec3.atLowerCornerOf(direction.getNormal()).scale(.5);
			obb.setCenter(new Vec3(.5, .5, .5).add(offset));
			CollisionResponse result = ContinuousOBBCollider.collideMany(colliders, scratch, obb,
				Vec3.ZERO, item.maxUpStep(), true);
			assertFiniteCollision(helper, result);
			helper.assertTrue(result.collisionResponse.dot(offset) > 0, "Overlap must push the item outwards");
			helper.assertTrue(Math.abs(result.collisionResponse.length() - .1251001) < 1E-6,
				"Offset item must retain its ordinary separation response");
		}

		// Zero projections on some axes must not suppress separation on the remaining axis.
		CollisionList thinCollider = new CollisionList();
		AABB thinBox = new AABB(0, 0, 0, 1, .25, 1);
		new CollisionList.Populate(thinCollider).consume(0, 0, 0, 1, .25, 1);
		obb.setCenter(new Vec3(.53125, .125, .5));
		CollisionResponse thinOverlap = ContinuousOBBCollider.collideMany(thinCollider, scratch, obb,
			Vec3.ZERO, item.maxUpStep(), true);
		assertFiniteCollision(helper, thinOverlap);
		helper.assertTrue(!obb.getAsAABB().move(thinOverlap.collisionResponse).intersects(thinBox),
			"Off-center item must be separated even when the other center coordinates coincide");

		obb.setCenter(new Vec3(4, 4, 4));
		CollisionResponse miss = ContinuousOBBCollider.collideMany(colliders, scratch, obb,
			Vec3.ZERO, item.maxUpStep(), true);
		assertFiniteCollision(helper, miss);
		helper.assertTrue(!miss.surfaceCollision && miss.collisionResponse.equals(Vec3.ZERO)
			&& miss.temporalResponse == 1, "Distant item must not collide");
		helper.succeed();
	}

	@GameTest(template = "issue10218_collision", batch = "regression_collision")
	public static void issue10218_itemAtDeployerCenter(CreateGameTestHelper helper) {
		CollisionList colliders = new CollisionList();
		AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN)
			.getCollisionShape(helper.getLevel(), BlockPos.ZERO).forAllBoxes(new CollisionList.Populate(colliders));
		helper.assertTrue(colliders.size == 1, "Downward deployer must have one collision box");
		Vec3 center = new Vec3(colliders.centerX[0], colliders.centerY[0], colliders.centerZ[0]);
		ItemEntity item = new ItemEntity(helper.getLevel(), center.x, center.y - .125, center.z,
			new ItemStack(Items.OAK_SAPLING), 0, 0, 0);
		OrientedBB obb = new OrientedBB(item.getBoundingBox().inflate(1E-7));
		helper.assertTrue(obb.getCenter().equals(center), "Sapling must be exactly centered in the deployer collider");
		CollisionResponse result = ContinuousOBBCollider.collideMany(colliders, new CollisionList(), obb,
			Vec3.ZERO, item.maxUpStep(), true);
		assertFiniteCollision(helper, result);
		helper.succeed();
	}

	@GameTest(template = "issue10218_collision", batch = "regression_collision", timeoutTicks = TEN_SECONDS)
	public static void issue10218_bearingWithCenteredItem(CreateGameTestHelper helper) {
		BlockPos motorPos = new BlockPos(3, 1, 3);
		BlockPos bearingPos = motorPos.above();
		helper.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(BlockStateProperties.FACING, Direction.UP));
		helper.setBlock(bearingPos, AllBlocks.MECHANICAL_BEARING.getDefaultState()
			.setValue(BlockStateProperties.FACING, Direction.UP));
		helper.setBlock(bearingPos.above(), Blocks.STONE);

		// Repro by atyrode: https://github.com/Creators-of-Create/Create/issues/10218#issuecomment-5314940422
		// On the bearing axis, the item stays exactly centered at every rotation angle.
		helper.runAtTickTime(20, () -> {
			MechanicalBearingBlockEntity bearing = helper.getBlockEntity(AllBlockEntityTypes.MECHANICAL_BEARING.get(), bearingPos);
			ControlledContraptionEntity contraption = bearing.getMovedContraption();
			helper.assertTrue(bearing.isRunning() && bearing.getSpeed() != 0 && contraption != null,
				"Bearing must assemble and rotate before testing collisions");
			helper.assertTrue(ContraptionHandler.loadedContraptions.get(helper.getLevel()).containsKey(contraption.getId()),
				"Bearing contraption must be registered for entity collisions");
			Vec3 center = contraption.getAnchorVec().add(.5, .5, .5);
			ItemEntity item = new ItemEntity(helper.getLevel(), center.x, center.y - .125, center.z,
				new ItemStack(Items.OAK_SAPLING), 0, 0, 0);
			item.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(item), "Item must be added to the world");
			try {
				for (float angle : new float[] {0, 15, 45, 90}) {
					contraption.setAngle(angle);
					item.setPos(center.x, center.y - .125, center.z);
					item.setDeltaMovement(Vec3.ZERO);
					helper.assertTrue(ContraptionCollider.worldToLocalPos(item.getBoundingBox().getCenter(), contraption)
						.equals(new Vec3(.5, .5, .5)), "Item must be centered in contraption-local space");
					// Call synchronously so an unfixed solver fails this test, rather than the server tick loop.
					ContraptionHandler.tick(helper.getLevel());
					helper.assertTrue(item.isAlive(), "Collision must not remove the item");
					helper.assertTrue(Double.isFinite(item.getX()) && Double.isFinite(item.getY())
						&& Double.isFinite(item.getZ()), "Collision must leave a finite item position");
				}
			} finally {
				// Also clean up on failure, before the normal world collision tick can see the item.
				item.discard();
				bearing.disassemble();
			}
			helper.runAfterDelay(2, helper::succeed);
		});
	}

	private static void assertFiniteCollision(CreateGameTestHelper helper, CollisionResponse result) {
		for (Vec3 vector : new Vec3[] {result.collisionResponse, result.normal, result.location})
			helper.assertTrue(Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z),
				"Collision response, normal and location must be finite");
		helper.assertTrue(Double.isFinite(result.temporalResponse) && result.temporalResponse >= 0
			&& result.temporalResponse <= 1, "Time of impact must be finite and within the tick");
	}
}
