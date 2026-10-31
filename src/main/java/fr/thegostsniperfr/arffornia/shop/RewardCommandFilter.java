package fr.thegostsniperfr.arffornia.shop;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides which stored reward commands may be executed.
 * <p>
 * Stop-gap: rewards are stored as free-form console commands. Until rewards become structured data delivered by the
 * API (see the rewards decision issue), only commands whose first word is in a configured allow-list are run.
 */
public final class RewardCommandFilter {
    private final Set<String> allowedRoots;

    public RewardCommandFilter(Collection<? extends String> allowedRoots) {
        this.allowedRoots = allowedRoots.stream()
                .map(RewardCommandFilter::normalizeRoot)
                .filter(root -> !root.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @param command the command after placeholder substitution, with or without a leading slash.
     * @return true if the command is non-empty, single-line and its root is allowed.
     */
    public boolean isAllowed(String command) {
        if (command == null) {
            return false;
        }
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
            return false;
        }
        String trimmed = command.strip();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            return false;
        }
        int space = trimmed.indexOf(' ');
        String root = space < 0 ? trimmed : trimmed.substring(0, space);
        return allowedRoots.contains(normalizeRoot(root));
    }

    public Set<String> allowedRoots() {
        return allowedRoots;
    }

    private static String normalizeRoot(String root) {
        String r = root == null ? "" : root.strip().toLowerCase(Locale.ROOT);
        return r.startsWith("/") ? r.substring(1) : r;
    }
}
