package edu.dartmouth.cs.project.sixpk.database;

import android.util.Log;

import java.util.ArrayList;
import java.util.Random;

import edu.dartmouth.cs.project.sixpk.Globals;
import edu.dartmouth.cs.project.sixpk.database.AbLog;


/*
workout flow:
    all possible exercises are stored in db
    main onCreate() { fetch all exercises }
    pass ArrayList of exercises, input time, input difficulty into Workout constructor
    create Workout object with exercises from different muscle groups
    after exercise, change feedback (difficulty) of each exercise
    or shared preferences for feedback instead of altering what’s in the db?
    add workout stats to stats page
 */

public class Workout {

    private long dateTime; // in milliseconds but we can convert it
    private int difficulty; //Easy, medium, hard correspond to 0-2
    private int[] exerciseIdList; // array of ablogIds
    private int[] feedBackList; // corresponds to ordering of exercise id list
    private int duration; // length of workout in seconds
    private long id; // database row

    // parallel arraylists to hold exercise IDs, respective durations
    ArrayList<Integer> exerciseIds = new ArrayList<>(); // not stored in db
    ArrayList<Integer> durations = new ArrayList<>(); // in seconds, not stored in db

    int[] durationList;

    private final int MUSCLE_GROUPS = 3; // how many muscle groups there are

    private final int[] DEFAULT_DURATIONS = new int[MUSCLE_GROUPS];

    public Workout() {}

    public Workout(ArrayList<AbLog> exercises, int time, int diff) {
        dateTime = System.currentTimeMillis(); // I guess?
        duration = time * 60;
        difficulty = diff;

        DEFAULT_DURATIONS[0] = 90;
        DEFAULT_DURATIONS[1] = 120;
        DEFAULT_DURATIONS[2] = 150;
        formWorkout(exercises, time, diff);
        exerciseIdList = convertToIntArray(exerciseIds);
        durationList = convertToIntArray(durations);
    }


    /*
    parameters: all exercises, input time in mins, input difficulty (0-2)

    sort exercises by muscle group
    sort muscle groups by difficulty
    set random number bounds based on input difficulty
    unique random numbers based on total time of workout
    shorten lengths of workouts if difficulty is higher
     */
    public void formWorkout(ArrayList<AbLog> exercises, int time, int diff) {
        int def_duration = DEFAULT_DURATIONS[diff];

        double divide = (time * 60) / (double) def_duration;
        double total = Math.ceil(divide); // approx how many workouts
        double subset = Math.ceil(total / MUSCLE_GROUPS); // how many workouts per muscle group

        // create the muscle list
        for (int m = 1; m <= MUSCLE_GROUPS; m++) { // muscle groups are labeled 1-3
            Log.d("m = ", m + "");
            // sort the exercises in one muscle group by feedback difficulty
            AbLog[] sorted = sortByDifficulty(chosenGroup(exercises, m));
            int min = 0, max = sorted.length; // max is exclusive

            if (diff == Globals.WORKOUT_EASY) { // only pick easier exercises if "easy" difficulty is selected
                max -= (int) Math.ceil(sorted.length / 3); // arbitrary pick the bottom 2/3 of exercises
            } else if (diff == Globals.WORKOUT_HARD) {
                min += (int) Math.ceil(sorted.length / 3);
            }

            int[] rands = uniqueRands( (int) subset, min, max);

            // add randomly selected exercises to the list
            for (int i = 0; i < rands.length; i++) {
                Log.d("i = ", i + "");
                int randomIndex = rands[i];
                if(sorted.length > randomIndex){
                    AbLog sortedIndex = sorted[randomIndex];
                    exerciseIds.add(sortedIndex.getAblogNumber());

                    // shorten or extend the duration based on feedback
                    // difficulty is 0-10, 5 is default
                    // so, do -5 to make it -5 to 5 and multiply by 5 seconds per unit
                    // then negate because easier difficulties are lower numbers which are longer workouts
                    int alter = -( (sorted[ rands[i] ].getDifficultyArray()[0] - 5) * 5);
                    alter = randInt(-5, 6) * 5;
//                    System.out.println("alter by " + alter + " seconds, to get " + "def_duration + alter" + " total time");
                    durations.add(def_duration + alter);
                }
            }
        }

        Log.d("LOOP IS ", "DONE");

        int extra = time * 60;
        for (Integer dur : durations) {
            extra -= dur;
        }

        for (Integer ex : exerciseIds) {
            System.out.println(ex);
        }

        correctTiming(extra);
        shuffle();
    }

    // returns a random integer between min inclusive and max exclusive
    private int randInt(int min, int max) {
        Random rand = new Random();
//        return rand.nextInt((max - min) + 1) + min;
        return rand.nextInt(max - min) + min;
    }

    // returns a list of unique random ints within a range
    private int[] uniqueRands(int num, int min, int max) {
        // prevents an infinite loop if there aren't enough exercises to fill the numbers
        int range = (max - 1) - min;
        if (range > num) num = range;

        int[] rands = new int[num];

        int i = 0;
        while (i < num) {
            int temp = randInt(min, max);
            boolean chosen = false;

            // check if the number has been chosen before
            if (i > 0) {
                for (int j = 0; j < i; j++) {
                    if (rands[j] == temp) {
                        chosen = true;
                    }
                }
            }
            if (chosen) continue;

            rands[i] = temp;
            i++;
        }
        return rands;
    }

    // return all exercises of one muscle group
    private ArrayList<AbLog> chosenGroup(ArrayList<AbLog> exercises, int muscleGroup) {
        ArrayList<AbLog> subset = new ArrayList<>();

        for (AbLog entry : exercises) {
            if (entry.getMuscleGroup() == muscleGroup) {
                subset.add(entry);
            }
        }

        return subset;
    }

    // returns exercise array from lowest to highest difficulty
    private AbLog[] sortByDifficulty(ArrayList<AbLog> subset) {
        int length = subset.size();
        AbLog[] sorted = new AbLog[length];

        for (int i = 0; i < length; i++) {
            sorted[i] = subset.get(i);
        }

        // using bubble sort for convenience
        for (int i = 0; i < length - 1; i++) {
            for (int j = 1; j < length; j++) {

                if (sorted[j - 1].getDifficultyArray()[0] > sorted[j].getDifficultyArray()[0]) {
                    sorted = swap(sorted, j - 1, j);
                }
            }
        }

        return sorted;
    }

    // takes the leftover seconds and either deletes workouts or spreads them out
    // to correct the total time
    private void correctTiming(int leftover) {
        while (true) {
            System.out.println(leftover);
            if (exerciseIds.size() <= 1) {
                break;
            }

            if (leftover < -90) {
                // get rid of last exercise if time is way over
                exerciseIds.remove(exerciseIds.size() - 1); // todo delete random workout not just last one
                leftover += durations.get(durations.size() - 1);
                durations.remove(durations.size() - 1);

            } else if (leftover >= -90 && leftover < -30) {
                // take away the leftover seconds over all exercises if it's too small
                int disp = 0;
                if (durations.size() != 0){
                    disp = 5 * (Math.round(( (-leftover) / durations.size()) / 5)); // round to nearest 5
                    leftover += disp * durations.size();
                }

                for (int i = 0; i < durations.size(); i++) {
                    durations.set(i, durations.get(i) - disp);
                }

            } else if (leftover >= -30 && leftover < 30) {
                // no editing if leftover is within a half minute
                break;

            } else {
                // disperse the leftover seconds over all exercises if it's too large
                int disp = 0;
                if (durations.size()!=0){
                    disp = 5 * (Math.round((leftover / durations.size()) / 5)); // round to nearest 5
                    leftover -= disp * durations.size();
                }

                for (int i = 0; i < durations.size(); i++) {
                    durations.set(i, durations.get(i) + disp);
                }
            }
        }
    }

    public void setDateTime(long dateTime) {
        this.dateTime = dateTime;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    private AbLog[] swap(AbLog[] list, int a, int b) {
        AbLog temp = list[b];
        list[b] = list[a];
        list[a] = temp;

        return list;
    }

    private void swap(int a, int b) {
        int id = exerciseIds.get(b);
        int dur = durations.get(b);

        exerciseIds.set(b, exerciseIds.get(a));
        durations.set(b, durations.get(a));

        exerciseIds.set(a, id);
        durations.set(a, dur);
    }

    // mix up the order of the exercises (ArrayList)
    private void shuffle() {
        int rand = randInt(1, 20); // arbitrary number of swaps
        int max = exerciseIds.size();

        for (int i = 0; i < rand; i++) {
            swap(randInt(0, max), randInt(0, max));
        }
    }

    private int[] convertToIntArray(ArrayList<Integer> al) {
        ArrayList<Integer> test = al;
        int[] new_list = new int[al.size()];
        for (int i = 0; i < new_list.length; i++) {
            new_list[i] = al.get(i).intValue();
        }
        return new_list;
    }


    public void removeExercise(int index) {
        // Remove from workout
        exerciseIds.remove(index);
        durations.remove(index);

        // Update int[]
        exerciseIdList = convertToIntArray(exerciseIds);
        durationList = convertToIntArray(durations);
    }

    public int[] getFeedBackList() {
        return feedBackList;
    }

    public void setFeedBackList(int[] feedBackList) {
        this.feedBackList = feedBackList;
    }

    public int[] getExerciseIdList() {
        return exerciseIdList;
    }

    public void setExerciseIdList(int[] exerciseIdList) {
        this.exerciseIdList = exerciseIdList;
    }

    public int[] getDurationList() {
        return durationList;
    }

    public void setDurationList(int[] durationList) {
        this.durationList = durationList;
    }

    public int getDuration() {
        return duration;
    }

    public long getDateTime() {
        return dateTime;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}



