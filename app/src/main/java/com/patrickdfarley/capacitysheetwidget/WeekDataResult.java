package com.patrickdfarley.capacitysheetwidget;

import java.util.List;

public class WeekDataResult {

    public List<CategoryScore> categories;
    public String SuccessScore;
    public String WeekLabel;

    public WeekDataResult(){
        categories=null;
    };

    public void addCategory(String name, String score){
        categories.add(new CategoryScore(name, score));
    }
}

class CategoryScore {
    String score;
    String name;

    CategoryScore(String name, String score){
        this.name = name;
        this.score = score;
    }
}

