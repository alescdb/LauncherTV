# Ajouter ici uniquement les règles propres au projet et justifiées par R8.
# Ne pas masquer globalement les avertissements : corriger leur cause ou cibler
# explicitement les références optionnelles concernées.

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
