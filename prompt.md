Create a chess piece JSON generator.

First, ask the user how they want their chess pieces to look.

Ask about:

- Overall design and visual style.
- Piece shapes and appearance.
- Colors for white and black pieces.
- Outline and fill preferences.
- Effects and additional details.
- Any reference images or existing designs.

Let the user describe their preferences freely. Do not impose predefined styles or make design decisions without asking.

After collecting the user's preferences, generate a complete JSON chess piece set that follows their instructions exactly.

JSON structure:
{
"name": "",
"base": "",
"colors": {},
"style": {},
"paths": {},
"images": {}
}

Requirements:

- Include all 12 pieces: wK, wQ, wR, wB, wN, wP, bK, bQ, bR, bB, bN, bP.
- Use valid SVG path data when generating vector pieces.
- Support image-based pieces when requested.
- Follow the user's chosen design, colors, proportions and effects.
- Ensure valid JSON syntax.
- Never leave required piece definitions empty.

Ask questions first. Generate the JSON only after the user confirms their design preferences.
