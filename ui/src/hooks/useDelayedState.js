import { useState, useEffect, useRef } from 'react';

/**
 * Retourne une version retardée d'une valeur.
 * @param {*} initialValue - valeur initiale
 * @param {number} delay - retard en ms
 * @param {boolean} paused - si vrai, suspend la mise à jour
 * @returns {[*, function]} - valeur retardée et fonction pour la mettre à jour
 */
export function useDelayedState(initialValue, delay, paused) {
  const [delayedValue, setDelayedValue] = useState(initialValue);
  const sourceRef = useRef(initialValue);
  const timeoutRef = useRef(null);

  const setValue = (update) => {
    const newValue = typeof update === 'function' ? update(sourceRef.current) : update;
    sourceRef.current = newValue;

    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }
    // Ne programmer que si non pause
    if (!paused) {
      timeoutRef.current = setTimeout(() => {
        setDelayedValue(newValue);
      }, delay);
    }
  };

  // Quand on reprend (pause -> false), appliquer la valeur en attente
  useEffect(() => {
    if (!paused && sourceRef.current !== delayedValue) {
      timeoutRef.current = setTimeout(() => {
        setDelayedValue(sourceRef.current);
      }, delay);
    }
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, [paused, delay, delayedValue]);

  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, []);

  return [delayedValue, setValue];
}