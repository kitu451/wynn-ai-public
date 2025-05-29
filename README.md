## Development Process & AI Collaboration

This mod, WYNN AI, was developed by natga999.

A significant portion of this project was developed with the extensive assistance of various AI language models, which served as powerful tools for brainstorming, code generation, API understanding, debugging complex issues, and providing architectural guidance. This collaborative approach with AI significantly accelerated development and helped in tackling intricate features.

The specific contributions and areas where AI provided notable assistance include:

*   **Google's Gemini (Gemini 2.5 Pro via web AI Studio):**
    *   Conceptualization and refinement of the Combat System.
    *   Design and implementation details for the Long-Distance Road Network, including `RoadNode`, `RoadNetworkManager`, and `LongDistancePathPlanner`.
    *   Detailed state machine design and GUI interaction logic for the `RepairStateManager`.
    *   Development of the `HighwaySplineStrategy` for smooth path following.

*   **OpenAI's ChatGPT (models GPT-4o and GPT-o4-mini):**
    *   Initial project setup and most of the structure.
    *   Core A* pathfinding logic in `PathFinder`.
    *   Rendering systems (`PathRenderer`, `RoadNetworkRenderer`).
    *   Development of `BasicPathAI` and its movement mechanics.
    *   Guidance on Fabric Mixins.
    *   Assistance with menu interactions and widget handling.
    *   Development of most manager classes and input handling.

*   **DeepSeek Coder:**
    *   Provided assistance with specific aspects of the A* algorithm and `BasicPathAI` refinements.

*   **Anthropic's Claude (Claude 3.7 Sonnet):**
    *   Used for general code-related questions, getting different perspectives on existing problems to find new solutions, and assistance with minor fixes or refactoring.

This transparent acknowledgement reflects the modern development landscape where AI tools are becoming integral partners in the creative and technical process.