// src/main/java/fr/thegostsniperfr/arffornia/client/gui/ProgressionGraphScreen.java
package fr.thegostsniperfr.arffornia.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Vector2i;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A screen that displays a progression graph, allowing users to view nodes and their connections.
 * It supports panning, cursor-centered zooming, and selecting nodes to view detailed information,
 * styled to match the web design's light theme and grid layout.
 */
public class ProgressionGraphScreen extends Screen {

    // --- DESIGN & THEME CONSTANTS ---

    private static final int BASE_GRID_CELL_SPACING = 48;
    private static final int BASE_NODE_DIAMETER = 48;
    private static final int BASE_ICON_DIAMETER = 32;
    private static final int BASE_LINE_THICKNESS = 3;

    // --- WEB THEME COLORS ---

    private static final int LINE_COLOR = 0xFFFF8C00;
    private static final int BACKGROUND_COLOR = 0xFFF2F2F2;
    private static final int GRID_MAIN_LINE_COLOR = 0xFFCCCCCC;
    private static final int GRID_SUB_LINE_COLOR = 0xFFE5E5E5;
    private static final int GRID_CROSS_COLOR = 0xFFAAAAAA;

    // --- TEXTURES ---

    private static final ResourceLocation NODE_BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath("arffornia", "textures/gui/node_background.png");
    private static final Map<String, ResourceLocation> ICON_TEXTURES = Map.of(
            "gear", ResourceLocation.fromNamespaceAndPath("arffornia", "textures/gui/node_icon_gear.png"),
            "image", ResourceLocation.fromNamespaceAndPath("arffornia", "textures/gui/node_icon_image.png")
    );

    // --- DATA STRUCTURES ---

    public record ProgressionNode(String id, String name, String description, int gridX, int gridY, String iconType) {}
    public record NodeLink(String sourceId, String targetId) {}

    private final List<ProgressionNode> nodes;
    private final List<NodeLink> links;
    private final Map<String, ProgressionNode> nodeMap;

    // --- UI STATE ---

    /** The world coordinate X that is at the top-left of the screen. */
    private double cameraX = 0;
    /** The world coordinate Y that is at the top-left of the screen. */
    private double cameraY = 0;

    private float zoom = 1.0f;
    private ProgressionNode selectedNode = null;
    private boolean isDragging = false;


    // --- CONSTRUCTOR & LIFECYCLE METHODS ---

    public ProgressionGraphScreen() {
        super(Component.empty()); // Title is removed.

        // Initialize data...
        this.nodes = List.of(
                new ProgressionNode("node1", "Getting Started", "This is the first step.", 2, 2, "gear"),
                new ProgressionNode("node2", "Advanced Machinery", "Build your first complex machine.", 2, 5, "gear"),
                new ProgressionNode("node3", "Exploration", "Discover new horizons.", 5, 3, "image"),
                new ProgressionNode("node4", "The Final Frontier", "Reach the ultimate goal.", 8, 5, "image")
        );

        this.links = List.of(
                new NodeLink("node1", "node2"),
                new NodeLink("node1", "node3"),
                new NodeLink("node3", "node4")
        );

        this.nodeMap = this.nodes.stream().collect(Collectors.toMap(ProgressionNode::id, node -> node));
    }

    /**
     * The main render loop, called every frame.
     * It orchestrates the drawing of all screen elements
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        this.drawConnections(guiGraphics);
        this.drawNodes(guiGraphics);

        if (this.selectedNode != null) {
            this.drawInfoPanel(guiGraphics, this.selectedNode);
        }
    }

    /**
     * Renders the background of the screen.
     */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        // Draw a solid color to hide the game world completely.
        guiGraphics.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);

        // Draw our custom grid on top of the solid background.
        this.drawGrid(guiGraphics);
    }


    // --- DRAWING METHODS ---

    /**
     * Draws the two-layer grid with crosses at intersections, matching the web design.
     */
    private void drawGrid(GuiGraphics guiGraphics) {
        final int mainGridCellSize = BASE_GRID_CELL_SPACING;
        final int subGridCellSize = mainGridCellSize / 4;
        final int crossSize = 5;

        double worldLeft = this.cameraX / this.zoom;
        double worldTop = this.cameraY / this.zoom;
        double worldRight = (this.cameraX + this.width) / this.zoom;
        double worldBottom = (this.cameraY + this.height) / this.zoom;

        // Draw the sub-grid
        drawGridLines(guiGraphics, subGridCellSize, worldLeft, worldTop, worldRight, worldBottom, GRID_SUB_LINE_COLOR);
        drawGridLines(guiGraphics, mainGridCellSize, worldLeft, worldTop, worldRight, worldBottom, GRID_MAIN_LINE_COLOR);

        // Draw crosses at main grid intersections
        float firstMainVertical = (float) (Math.floor(worldLeft / mainGridCellSize) * mainGridCellSize);
        float firstMainHorizontal = (float) (Math.floor(worldTop / mainGridCellSize) * mainGridCellSize);
        for (float y = firstMainHorizontal; y < worldBottom; y += mainGridCellSize) {
            for (float x = firstMainVertical; x < worldRight; x += mainGridCellSize) {
                int crossScreenX = (int) (x * this.zoom - this.cameraX);
                int crossScreenY = (int) (y * this.zoom - this.cameraY);
                int scaledCrossSize = (int)(crossSize * this.zoom);
                if (scaledCrossSize < 3) continue;

                guiGraphics.fill(crossScreenX - scaledCrossSize/2, crossScreenY, crossScreenX + scaledCrossSize/2 + 1, crossScreenY + 1, GRID_CROSS_COLOR);
                guiGraphics.fill(crossScreenX, crossScreenY - scaledCrossSize/2, crossScreenX + 1, crossScreenY + scaledCrossSize/2 + 1, GRID_CROSS_COLOR);
            }
        }
    }

    /**
     * A helper method to draw a set of parallel grid lines, either vertically or horizontally.
     * @param guiGraphics The GuiGraphics instance.
     * @param cellSize The spacing between lines in world coordinates.
     * @param worldLeft The left edge of the visible world.
     * @param worldTop The top edge of the visible world.
     * @param worldRight The right edge of the visible world.
     * @param worldBottom The bottom edge of the visible world.
     * @param color The color of the lines.
     */
    private void drawGridLines(GuiGraphics guiGraphics, int cellSize, double worldLeft, double worldTop, double worldRight, double worldBottom, int color) {
        // Draw vertical lines
        float firstVerticalLine = (float) (Math.floor(worldLeft / cellSize) * cellSize);
        for (float x = firstVerticalLine; x < worldRight; x += cellSize) {
            int screenX = (int) (x * this.zoom - this.cameraX);
            guiGraphics.fill(screenX, 0, screenX + 1, this.height, color);
        }

        // Draw horizontal lines
        float firstHorizontalLine = (float) (Math.floor(worldTop / cellSize) * cellSize);
        for (float y = firstHorizontalLine; y < worldBottom; y += cellSize) {
            int screenY = (int) (y * this.zoom - this.cameraY);
            guiGraphics.fill(0, screenY, this.width, screenY + 1, color);
        }
    }

    /**
     * Iterates through all links and draws them using the appropriate method.
     */
    private void drawConnections(GuiGraphics guiGraphics) {
        for (NodeLink link : links) {
            ProgressionNode source = nodeMap.get(link.sourceId());
            ProgressionNode target = nodeMap.get(link.targetId());
            if (source == null || target == null) continue;

            if (source.gridX() == target.gridX() || source.gridY() == target.gridY()) {
                drawStraightLine(guiGraphics, source, target);
            } else {
                drawElbowConnection(guiGraphics, source, target);
            }
        }
    }

    /**
     * Draws all nodes onto the screen.
     */
    private void drawNodes(GuiGraphics guiGraphics) {
        for (ProgressionNode node : nodes) {
            Vector2i nodePos = getScreenPosForNode(node);
            int nodeDiameter = (int)(BASE_NODE_DIAMETER * this.zoom);
            int iconDiameter = (int)(BASE_ICON_DIAMETER * this.zoom);

            // Don't draw nodes that are way off-screen
            if (nodePos.x + nodeDiameter/2 < 0 || nodePos.x - nodeDiameter/2 > this.width ||
                    nodePos.y + nodeDiameter/2 < 0 || nodePos.y - nodeDiameter/2 > this.height) {
                continue;
            }

            int nodeScreenX = nodePos.x - nodeDiameter / 2;
            int nodeScreenY = nodePos.y - nodeDiameter / 2;
            guiGraphics.blit(NODE_BACKGROUND_TEXTURE, nodeScreenX, nodeScreenY, 0, 0, nodeDiameter, nodeDiameter, nodeDiameter, nodeDiameter);

            ResourceLocation iconTexture = ICON_TEXTURES.get(node.iconType());
            if (iconTexture != null) {
                int iconX = nodeScreenX + (nodeDiameter - iconDiameter) / 2;
                int iconY = nodeScreenY + (nodeDiameter - iconDiameter) / 2;
                guiGraphics.blit(iconTexture, iconX, iconY, 0, 0, iconDiameter, iconDiameter, iconDiameter, iconDiameter);
            }
        }
    }

    /**
     * Draws a stepped connection between two nodes.
     */
    private void drawElbowConnection(GuiGraphics guiGraphics, ProgressionNode source, ProgressionNode target) {
        Vector2i start = getScreenPosForNode(source);
        Vector2i end = getScreenPosForNode(target);
        int lineThickness = (int)Math.max(1, BASE_LINE_THICKNESS * this.zoom);

        // Find the corner point for the elbow
        int midX = start.x + (end.x - start.x) / 2;
        Vector2i elbow1 = new Vector2i(midX, start.y);
        Vector2i elbow2 = new Vector2i(midX, end.y);

        // Draw the three segments
        drawThickLine(guiGraphics, start, elbow1, lineThickness, LINE_COLOR);
        drawThickLine(guiGraphics, elbow1, elbow2, lineThickness, LINE_COLOR);
        drawThickLine(guiGraphics, elbow2, end, lineThickness, LINE_COLOR);
    }

    /**
     * Draws a straight line between two nodes.
     */
    private void drawStraightLine(GuiGraphics guiGraphics, ProgressionNode source, ProgressionNode target) {
        Vector2i start = getScreenPosForNode(source);
        Vector2i end = getScreenPosForNode(target);
        int lineThickness = (int)Math.max(1, BASE_LINE_THICKNESS * this.zoom);
        drawThickLine(guiGraphics, start, end, lineThickness, LINE_COLOR);
    }

    /**
     * Draws a line of a specific thickness between two points.
     */
    private void drawThickLine(GuiGraphics guiGraphics, Vector2i p1, Vector2i p2, int thickness, int color) {
        int x1 = p1.x, y1 = p1.y, x2 = p2.x, y2 = p2.y;
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            guiGraphics.fill(x1 - thickness / 2, y1 - thickness / 2, x1 + thickness / 2 + thickness % 2, y1 + thickness / 2 + thickness % 2, color);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 < dx) { err += dx; y1 += sy; }
        }
    }

    /**
     * Renders the info panel when a node is selected.
     */
    private void drawInfoPanel(GuiGraphics guiGraphics, ProgressionNode node) {
        int panelWidth = 170;
        int panelHeight = this.height - 40;
        int panelX = this.width - panelWidth - 20;
        int panelY = 20;
        int padding = 8;
        int textColor = 0xFF_FFFFFF;
        int titleColor = 0xFF_FFAA00;

        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0_101010);

        int currentY = panelY + padding;
        guiGraphics.drawString(this.font, node.name(), panelX + padding, currentY, titleColor);
        currentY += font.lineHeight + 4;

        guiGraphics.drawString(this.font, "Description:", panelX + padding, currentY, textColor);
        currentY += font.lineHeight;

        List<FormattedCharSequence> descLines = this.font.split(Component.literal(node.description()), panelWidth - 2 * padding);
        for(FormattedCharSequence line : descLines) {
            guiGraphics.drawString(this.font, line, panelX + padding, currentY, textColor);
            currentY += font.lineHeight;
        }
    }


    // --- INTERACTION HANDLERS ---

    /**
     * Handles mouse scrolling to zoom in and out, centered on the cursor's position.
     * @return true if the event was handled.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (hasControlDown()) {
            // 1. Get the world coordinates under the mouse before zooming
            double mouseWorldXBefore = (mouseX + this.cameraX) / this.zoom;
            double mouseWorldYBefore = (mouseY + this.cameraY) / this.zoom;

            // 2. Apply the new zoom level
            float zoomFactor = (verticalAmount > 0) ? 1.1f : (1.0f / 1.1f);
            this.zoom = Mth.clamp(this.zoom * zoomFactor, 0.15f, 2.5f);

            // 3. Calculate the new camera position to keep the world point under the mouse stationary
            this.cameraX = mouseWorldXBefore * this.zoom - mouseX;
            this.cameraY = mouseWorldYBefore * this.zoom - mouseY;

            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Handles mouse clicks for node selection and starting a canvas drag.
     * @return true if the event was handled.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            // Check for node collision in world space
            double nodeCheckRadius = (BASE_NODE_DIAMETER / 2.0);

            for (ProgressionNode node : nodes) {
                double nodeWorldX = node.gridX() * BASE_GRID_CELL_SPACING;
                double nodeWorldY = node.gridY() * BASE_GRID_CELL_SPACING;

                double mouseWorldX = (mouseX + this.cameraX) / this.zoom;
                double mouseWorldY = (mouseY + this.cameraY) / this.zoom;

                double dist = Math.sqrt(Math.pow(nodeWorldX - mouseWorldX, 2) + Math.pow(nodeWorldY - mouseWorldY, 2));

                if (dist <= nodeCheckRadius) {
                    this.selectedNode = node;
                    this.isDragging = false;
                    return true;
                }
            }

            // If no node was clicked, start a drag operation
            this.selectedNode = null;
            this.isDragging = true;

            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Handles dragging the canvas to move the camera.
     * @return true if the event was handled.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDragging && button == 0) {
            this.cameraX -= dragX;
            this.cameraY -= dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * Handles releasing the mouse button to stop dragging.
     * @return true if the event was handled.
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isDragging) {
            this.isDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }


    // --- COORDINATE UTILITIES ---

    /**
     * Converts a node's grid coordinates to its center position on the screen.
     * @param node The node to position.
     * @return A Vector2i containing the screen-space coordinates.
     */
    private Vector2i getScreenPosForNode(ProgressionNode node) {
        return worldToScreen(node.gridX() * BASE_GRID_CELL_SPACING, node.gridY() * BASE_GRID_CELL_SPACING);
    }

    /**
     * Converts world coordinates to screen coordinates based on camera and zoom.
     * @param worldX The world X coordinate.
     * @param worldY The world Y coordinate.
     * @return A Vector2i containing the screen-space coordinates.
     */
    private Vector2i worldToScreen(double worldX, double worldY) {
        int screenX = (int) (worldX * this.zoom - this.cameraX);
        int screenY = (int) (worldY * this.zoom - this.cameraY);
        return new Vector2i(screenX, screenY);
    }


    // --- SCREEN BEHAVIOR ---

    /**
     * @return false, so the game world does not pause when this screen is open.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}