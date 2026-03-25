package com.medievalmarket.service;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class NameGenerator {

    private static final List<String> FIRST_NAMES = List.of(
        "Aldric", "Mildred", "Godwin", "Edith", "Leofric",
        "Wulfric", "Hilda", "Oswin", "Aelswith", "Thorbert",
        "Sigrid", "Aethelred", "Brunhilde", "Cynric", "Matilda",
        "Oswulf", "Aelfred", "Gytha", "Cuthbert", "Rowena",
        "Beorn", "Eadric", "Freya", "Gunhild", "Halfdan",
        "Ingrid", "Jorvik", "Ketil", "Leofwine", "Morwenna"
    );

    private static final List<String> EPITHETS = List.of(
        "the Bold", "the Cunning", "the Stout", "the Shrewd",
        "the Fearless", "the Wise", "the Greedy", "the Lucky",
        "the Swift", "the Grim", "the Fair", "the Lean",
        "the Elder", "the Younger", "the Pious", "the Crafty"
    );

    private static final List<String> PLACE_SUFFIXES = List.of(
        "of Stonekeep", "of Ironhaven", "of the Moors",
        "of Ashford", "of Greywall", "of the Valley",
        "of Ravenport", "of the Fens", "of Coldwater",
        "of Dunmere", "of the Highlands", "of Saltmarsh"
    );

    public String generate() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        String firstName = FIRST_NAMES.get(rng.nextInt(FIRST_NAMES.size()));
        String suffix = rng.nextBoolean()
            ? EPITHETS.get(rng.nextInt(EPITHETS.size()))
            : PLACE_SUFFIXES.get(rng.nextInt(PLACE_SUFFIXES.size()));
        return firstName + " " + suffix;
    }
}
