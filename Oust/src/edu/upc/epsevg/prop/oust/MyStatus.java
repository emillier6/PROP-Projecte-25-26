/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package edu.upc.epsevg.prop.oust;

/**
 * Classe que emmagatzema informació precalculada sobre l'estat del joc.
 * Utilitzada per GameStatusTunned per optimitzar les heurístiques.
 * 
 * @author Alex Aranda & Erik Millier
 */
public class MyStatus {
    public int stonesP1 = 0;
    public int stonesP2 = 0;
    public int biggestGroupP1 = 0;
    public int biggestGroupP2 = 0;
    public boolean lastMoveWasCapture = false;
}
