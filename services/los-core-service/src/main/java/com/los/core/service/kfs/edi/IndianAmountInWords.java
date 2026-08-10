package com.los.core.service.kfs.edi;

/**
 * Converts integer rupee amounts to words (Indian lakh grouping), ported from legacy sanction letter flow.
 */
public final class IndianAmountInWords {

    private IndianAmountInWords() {
    }

    public static String convert(int number) {
        if (number == 0) {
            return "zero";
        }
        if (number < 0) {
            return "minus " + convert(-number);
        }

        String[] units = {"", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
        String[] teens = {
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
        };
        String[] tens = {"", "ten", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};

        String words = "";
        int lakh = number / 100_000;
        number %= 100_000;
        int thousand = number / 1_000;
        number %= 1_000;
        int hundred = number / 100;
        number %= 100;
        int ten = number / 10;
        int unit = number % 10;

        if (lakh > 0) {
            words += helper(lakh, units, teens, tens) + " lakh";
        }
        if (thousand > 0) {
            words += (words.isEmpty() ? "" : " and ") + helper(thousand, units, teens, tens) + " thousand";
        }
        if (hundred > 0) {
            words += (words.isEmpty() ? "" : " ") + helper(hundred, units, teens, tens) + " hundred";
        }
        if (ten > 0 || unit > 0) {
            words += (words.isEmpty() ? "" : " and ") + helperTensAndUnits(ten, unit, units, teens, tens);
        }
        return words.trim();
    }

    private static String helper(int num, String[] units, String[] teens, String[] tens) {
        if (num == 0) {
            return "";
        }
        if (num < 10) {
            return units[num];
        }
        if (num < 20) {
            return teens[num - 10];
        }
        return tens[num / 10] + (num % 10 != 0 ? "-" + units[num % 10] : "");
    }

    private static String helperTensAndUnits(int ten, int unit, String[] units, String[] teens, String[] tens) {
        if (ten == 0) {
            return units[unit];
        }
        if (ten == 1) {
            return teens[unit];
        }
        return tens[ten] + (unit != 0 ? "-" + units[unit] : "");
    }
}
