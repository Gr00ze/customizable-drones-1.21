package com.grooze.drone.event;

import com.grooze.drone.entity.DroneEntity;
import com.grooze.drone.entity.TestDroneEntity;
import com.grooze.drone.render.DebugHud;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import static com.grooze.drone.event.KeyBinds.*;

public class ClientEvents {

    public static Integer IDTracking = null;

    public static void registerEvents(){

        // Aggiungiamo un evento che viene chiamato alla fine di ogni tick del client

        //ClientTickEvents.END_CLIENT_TICK.register(ClientEvents::onDroneKeysPressed);
        //ClientTickEvents.END_CLIENT_TICK.register(ClientEvents::onDroneUpKeyPressed);
        //Mando lo stato dei pulsanti ogni volta
        ClientTickEvents.END_CLIENT_TICK.register(ClientEvents::onKeysPressed);


            ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null)return;



            Vec3d playerVelocity = player.getVelocity();
            Vec3d normalizedVelocityVector = new Vec3d(Math.signum(playerVelocity.x), Math.signum(playerVelocity.y), Math.signum(playerVelocity.z));

            if (player.getVehicle() instanceof TestDroneEntity testDroneEntity){
                IDTracking = testDroneEntity.getId();
            }
            if (IDTracking != null){
                TestDroneEntity testDroneEntity = (TestDroneEntity) client.world.getEntityById(IDTracking);
                if(testDroneEntity == null)return;
                DebugHud.addLogMessage(testDroneEntity.getPos());
                DebugHud.addLogMessage(testDroneEntity.getYaw());
                DebugHud.addLogMessage(testDroneEntity.getVelocity());
                DebugHud.addLogMessage(playerVelocity);
            }


        });



    }

    private static void onKeysPressed(MinecraftClient minecraftClient) {
        //Questo metodo mi piace
        ClientPlayerEntity player = minecraftClient.player;
        if (player == null)
            //not in game
            return;

        if(! (player.getControllingVehicle() instanceof TestDroneEntity))
            //not on drone
            return;
        Entity vehicle = player.getControllingVehicle();
        boolean forward = player.input.pressingForward;
        boolean back = player.input.pressingBack;
        boolean left = player.input.pressingLeft;
        boolean right = player.input.pressingRight;
        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean up = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        ClientNetwork.sendDroneKeysPacket(vehicle.getId(), forward, back, left, right, up, false);

        TestDroneEntity drone = (TestDroneEntity) vehicle;
        drone.right = right;
        drone.left = left;

    }

    private static void onDroneUpKeyPressed(MinecraftClient minecraftClient) {
        ClientPlayerEntity clientPlayer = minecraftClient.player;
        if(clientPlayer == null)return;

        boolean playerJumping = clientPlayer.input.jumping;

        DebugHud.addLogMessage(playerJumping);

        Entity vehicle = clientPlayer.getControllingVehicle();
        //ottengo l' id che ci serve se è un drone
        if (vehicle instanceof TestDroneEntity && playerJumping)
            ClientNetwork.sendUpPacket(vehicle.getId());

    }

    static void onDroneKeysPressed(MinecraftClient client){
        ClientPlayerEntity player = client.player;


        // Verifico di essere in game e non che ci siano conflitti di tasti
        if (player == null || droneUP.wasPressed() == droneDown.wasPressed()) {
            return;
        }


        int vehicleId;
        Entity vehicle = client.player.getControllingVehicle();
        //ottengo l' id che ci serve se è un drone
        if (vehicle instanceof DroneEntity || vehicle instanceof TestDroneEntity){
            vehicleId = vehicle.getId();
        }else{
            return;
        }

        if(droneUP.isPressed()){
            //System.out.println("UP");
            ClientNetwork.sendUpPacket(vehicleId);
        } else if (droneDown.isPressed()) {
            //System.out.println("DOWN");
            ClientNetwork.sendDownPacket(vehicleId);
        }
        //System.out.println("premuti");

    }
}
