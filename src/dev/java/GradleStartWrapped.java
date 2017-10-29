public class GradleStartWrapped extends GradleStart {

    public static void main(String[] args) throws Throwable {
        // hack natives.
        //If you wish to use this in dev you MUST make the parent method public.
        hackNatives();

        // launch
        new GradleStartWrapped().launch(args);
    }

    @Override
    protected String getBounceClass() {
        return "net.minecraft.launchwrapper.Launch";
    }

    @Override
    protected String getTweakClass() {
        return "net.covers1624.forceddeobf.launch.FMLTweakWrapper";
    }
}
