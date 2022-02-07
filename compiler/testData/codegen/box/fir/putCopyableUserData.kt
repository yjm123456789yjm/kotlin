// TARGET_BACKEND: JVM
// WITH_STDLIB
// FILE: UserDataHolder.java

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UserDataHolder {
    /**
     * @return a user data value associated with this object. Doesn't require read action.
     */
    @Nullable
    <T> T getUserData(@NotNull Key<T> key);
}

// FILE: UserDataHolderBase.java

import org.jetbrains.annotations.NotNull;

public class UserDataHolderBase implements UserDataHolder {
    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
        return null;
    }
}

// FILE: RunConfigurationBase.java

public abstract class RunConfigurationBase<R> extends UserDataHolderBase {}

// FILE: Key.java

import org.jetbrains.annotations.NotNull;

public class Key<K> {
    public Key(@NotNull String name)
    {
    }

    @NotNull
    public static <T> Key<T> create(@NotNull String name) {
        return new Key<T>(name);
    }
}

// FILE: test.kt

private val RUN_EXTENSIONS = Key.create<List<String>>("run.extension.elements")

fun foo(configuration: RunConfigurationBase<*>) {
    configuration.getUserData(RUN_EXTENSIONS)
}

fun box(): String {
    return "OK"
}
