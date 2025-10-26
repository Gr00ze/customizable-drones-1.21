package com.grooze.drone.entity;

import com.grooze.drone.event.DroneSyncS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import static com.grooze.drone.util.DebugLog.print;


public class TestDroneEntity extends Entity implements Movable{
    public boolean isPowerOn = false;
    public boolean hadPassenger = false;

    public static final float
            ROTATION_SPEED = 5F,
            FORWARD_SPEED = 0.5F,
            UP_SPEED = 0.1F;

    public static float verticalSpeed = 0.2F;

    public static final TrackedData<Float> TARGET_Y = DataTracker.registerData(TestDroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static  Boolean WANT_GO_UP = false;

    public boolean forward = false, back = false, right = false, left = false, up = false;

    public TestDroneEntity(EntityType<?> type, World world) {
        super(type, world);
        noClip = false;
    }



    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        //Cannot be null
        builder.add(TARGET_Y, 0F);

    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        //Nothing to load for now

    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        //Nothing to save for now
    }

    @Override
    public void tick() {
        super.tick();
        //Se cavalco comanda il client se non cavalco comanda il server sull' entità

        /*
        Con questo codice la posizione e rotazione del drone si aggiorna negli altri client a scatti
        Forse dovrei interpolare lato client?
        PS
        Controllando meglio non lo fa
        Il problema è la sincronizzazione dell entità quando il client smonta da esso

        */
        if(hasPassengers()){
            hadPassenger = false;
        }
        else{
            hadPassenger = true;
        }


        if(!this.getWorld().isClient){
            double forward = this.forward ? FORWARD_SPEED : this.back ? - FORWARD_SPEED : 0;
            double sideways = this.left ? 1 : this.right ? -1 : 0;
            float yawChange = (float) - sideways * ROTATION_SPEED;
            this.setYaw(this.getYaw() + yawChange);

            //TODO Dovrei mandare i pacchetti solo i player vicini invece che tutti per alleggerire qualora servisse
            //TODO Dovrei mandare i pacchetti solo quando una rotazione avviene per alleggerire qualora servisse
            for (PlayerEntity player : this.getWorld().getPlayers()) {
                ServerPlayNetworking.send((ServerPlayerEntity) player, DroneSyncS2C.droneSyncS2C(this));
                //System.out.println("Mando pacchetto");
            }


            // Converte lo yaw attuale del drone in radianti
            double radYaw = Math.toRadians(this.getYaw());


            double moveX = -Math.sin(radYaw) * forward;
            double moveZ = Math.cos(radYaw) * forward;
            double moveY = up ? UP_SPEED: -UP_SPEED;
            // TEST testing position reset with y = 0

            Vec3d droneVelocity = new Vec3d(moveX, moveY, moveZ);
            if (!this.hasPassengers())
                droneVelocity.multiply(0);

            this.setVelocity(droneVelocity);


            print(0, "Pos: "+this.getPos());

        }

        this.move(MovementType.SELF,this.getVelocity());
    }

    private void moveUp() {
        //Non uso
        if (WANT_GO_UP){
            this.addVelocity(0,0.1,0);
            WANT_GO_UP = false;
        }
    }


    @Override
    protected double getGravity() {
        //Non uso
        return 0.01;
    }

    private void slowDownOnNoInput() {
        if (!(this.getFirstPassenger() instanceof PlayerEntity)){
            this.setVelocity(this.getVelocity().multiply(0,1,0));
        }
    }


    public void moveOnDriverInput(){
        if (this.getFirstPassenger() instanceof PlayerEntity player){


            double forward = player.forwardSpeed * FORWARD_SPEED;
            double sideways = player.sidewaysSpeed;

            //print(0,"SidewaySpeed "+sideways,this.getWorld().isClient);


            float yawChange = (float) - sideways * ROTATION_SPEED;
            this.setYaw(this.getYaw() + yawChange);


            // Converte lo yaw attuale del drone in radianti
            double radYaw = Math.toRadians(this.getYaw());


            double moveX = -Math.sin(radYaw) * forward;
            double moveZ = Math.cos(radYaw) * forward;

            Vec3d droneVelocity = new Vec3d(moveX, this.getVelocity().y, moveZ);
            this.setVelocity(droneVelocity);



        }
    }

    public void tryToReachTargetY(){
        //Non uso
        float targetY = dataTracker.get(TARGET_Y);
        Vec3d pos = getPos();
        double currentY = pos.getY();
        double deltaY = targetY - currentY;


        double speedFactor = 0.1; // Fattore di smorzamento della velocità
        double threshold = 0.01; // Soglia per considerare il drone fermo

        if (Math.abs(deltaY) < threshold)
            this.setVelocity(this.getVelocity().multiply(1, 0, 1));
        else if(targetY > currentY)
            this.addVelocity(0, verticalSpeed, 0);
        else
            this.addVelocity(0, -this.getVelocity().y - 0.2, 0);

    }



    public void sendUpdate(){
        Packet<ServerPlayPacketListener> packet = new VehicleMoveC2SPacket(this);
        if(this.getWorld().isClient){
            getWorld().sendPacket(packet);
        }

        /*
            Packet<ServerPlayPacketListener> packet = new VehicleMoveC2SPacket(this);
            ServerWorld serverWorld = (ServerWorld) this.getWorld();
            serverWorld.getChunkManager().sendToNearbyPlayers(this, packet);
            */


    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        //Testato funziona regolarmente
        ActionResult actionResult = super.interact(player, hand);
        if (actionResult != ActionResult.PASS) {
            return actionResult;
        } else if (player.shouldCancelInteraction()) {
            return ActionResult.PASS;
        } else {
            if (!this.getWorld().isClient) {

                return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
            } else {
                return ActionResult.SUCCESS;
            }
        }
    }

    @Override
    public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
        //Testato funziona regolarmente
        return super.interactAt(player, hitPos, hand);
    }

    public boolean isCollidable() {
        //per le collisioni
        return true;
    }

    public boolean canHit() {
        //per interagire
        return !this.isRemoved();
    }

    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengerList().size() < this.getMaxPassengers() && !this.isSubmergedIn(FluidTags.WATER);
    }

    protected int getMaxPassengers() {
        return 1;
    }

    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity firstPassenger = this.getFirstPassenger();
        LivingEntity controllingPassenger;
        if (firstPassenger instanceof LivingEntity livingEntity) {
            controllingPassenger = livingEntity;
        } else {
            controllingPassenger = super.getControllingPassenger();
        }
        return controllingPassenger;
    }


    //should be on a interface or abstract class
    public void move(int action) {
        //maybe this isn't good because lack of sync
        /*

        if (action == 1)
            this.addVelocity(0,0.2,0);
        */

        if (action == 1)
            WANT_GO_UP = true;

        /*
        float targetY = dataTracker.get(TARGET_Y);
        double currentY = this.getPos().getY();
        if (action == 1)
            dataTracker.set(TARGET_Y, targetY + verticalSpeed + 1);
        else if(action == 0 && (!this.isOnGround() || (this.isOnGround() && currentY - targetY < 2)))
            dataTracker.set(TARGET_Y, targetY - verticalSpeed - 1);
        */

    }



    public boolean shouldReachTargetY(){
        return isPowerOn;
    }



    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        this.dataTracker.set(TARGET_Y, (float) this.getBlockY());
        this.isPowerOn = false;
        System.out.println("Spawned");

    }


}
