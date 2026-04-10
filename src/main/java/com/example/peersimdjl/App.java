package com.example.peersimdjl;

import peersim.Simulator;

public class App {

    public static void main(String[] args) {
        try {
            System.out.println("Démarrage de la simulation PeerSim...");
            Simulator.main(new String[]{"C:\\Users\\Admin\\projects\\peersim-djl 2\\src\\main\\resources\\peersim.cfg"});
            System.out.println("Simulation terminée.");
        } catch (Exception e) {
            System.err.println("Erreur lors de la simulation : " + e.getMessage());
            e.printStackTrace();
        }
    }
}