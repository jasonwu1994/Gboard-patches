package dev.jason.gboardpatches.extension.calculator;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

/** Immutable identity of one displayed calculator suggestion. */
final class GboardCalculatorDisplaySnapshot {
    private final WeakReference<InputMethodService> service;
    private final WeakReference<FrameLayout> host;
    private final WeakReference<InputConnection> connection;
    private final long sessionGeneration;
    private final GboardCalculatorSuggestion.Candidate candidate;

    GboardCalculatorDisplaySnapshot(InputMethodService service, FrameLayout host,
            InputConnection connection, long sessionGeneration,
            GboardCalculatorSuggestion.Candidate candidate) {
        this.service = new WeakReference<>(service);
        this.host = new WeakReference<>(host);
        this.connection = new WeakReference<>(connection);
        this.sessionGeneration = sessionGeneration;
        this.candidate = candidate;
    }

    boolean matchesCurrent(InputMethodService currentService, View currentView,
            InputConnection currentConnection, long currentGeneration,
            GboardCalculatorSuggestion.Candidate currentCandidate) {
        return matchesCurrent(
                currentService, currentView, currentConnection, currentGeneration)
                && matchesCandidate(currentCandidate);
    }

    boolean matchesCurrent(InputMethodService currentService, View currentView,
            InputConnection currentConnection, long currentGeneration) {
        return currentService != null
                && service.get() == currentService
                && host.get() == currentView
                && currentConnection != null
                && connection.get() == currentConnection
                && sessionGeneration == currentGeneration;
    }

    boolean matchesCandidate(GboardCalculatorSuggestion.Candidate currentCandidate) {
        return candidate != null && currentCandidate != null
                && candidate.getExpression().equals(currentCandidate.getExpression())
                && candidate.getResult().equals(currentCandidate.getResult())
                && candidate.getDeleteBeforeCursorCount()
                == currentCandidate.getDeleteBeforeCursorCount();
    }

    InputMethodService service() {
        return service.get();
    }

    long sessionGeneration() {
        return sessionGeneration;
    }

    GboardCalculatorSuggestion.Candidate candidate() {
        return candidate;
    }
}
