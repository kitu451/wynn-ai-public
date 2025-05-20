package net.natga999.wynn_ai.tasks;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Deque;

public class TaskManager {
    private final Deque<Task> stack = new ArrayDeque<>();

    /** Called each tick. */
    public void tick(MinecraftClient client) {
        // 1) Clean up finished tasks
        while (!stack.isEmpty() && !stack.peek().isActive()) {
            stack.pop().stop(client);
        }

        // 2) If nothing left, you can push a default “idle” task
        if (stack.isEmpty()) {
            push(new IdleTask());
        }

        // 3) Run the task at the top
        Task current = stack.peek();
        assert current != null;
        current.tick(client);
    }

    /** Pushes a new task, preempting lower‐priority ones automatically. */
    public void push(Task task) {
        // remove any lower‐priority tasks first
        while (!stack.isEmpty()
                && stack.peek().getPriority() > task.getPriority()) {
            stack.pop().stop(null);
        }
        stack.push(task);
        task.start(null);
    }

    /** Replace current with a new task at same priority. */
    public void replace(Task task) {
        if (!stack.isEmpty()) stack.pop().stop(null);
        push(task);
    }
}