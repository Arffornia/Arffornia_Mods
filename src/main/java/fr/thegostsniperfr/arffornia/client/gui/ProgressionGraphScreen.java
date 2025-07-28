package fr.thegostsniperfr.arffornia.client.gui;


import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.client.ClientProgressionData;
import fr.thegostsniperfr.arffornia.client.util.SoundUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;

import java.util.*;
import java.util.stream.Collectors;

import static fr.thegostsniperfr.arffornia.Arffornia.ARFFORNA_API_SERVICE;

/**
 * A screen that displays a progression graph, allowing users to view nodes and their connections.
 * It supports panning, cursor-centered zooming, and selecting nodes to view detailed information.
 * This version fetches its data from a remote API and is styled to match the web design's light theme.
 */
public class ProgressionGraphScreen extends Screen {

    // --- DESIGN & THEME CONSTANTS ---

    private static final int BASE_GRID_CELL_SPACING = 64;
    private static final int BASE_NODE_DIAMETER = 64;
    private static final int BASE_ICON_DIAMETER = 20;
    private static final int BASE_LINE_THICKNESS = 3;

    // --- WEB THEME COLORS ---

    private static final int LINE_COLOR = 0xFFFF8C00;
    private static final int BACKGROUND_COLOR = 0xFFF2F2F2;
    private static final int GRID_MAIN_LINE_COLOR = 0xFFCCCCCC;
    private static final int GRID_SUB_LINE_COLOR = 0xFFE5E5E5;
    private static final int GRID_CROSS_COLOR = 0xFFAAAAAA;
    private static final int INFO_TEXT_COLOR = 0xFF333333;

    // --- TEXTURES ---

    private static final ResourceLocation NODE_BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath("arffornia", "textures/gui/node_background.png");

    // --- DATA STRUCTURES ---

    /** Represents a single, fully-detailed node in the progression graph. */
    public record ProgressionNode(int id, String name, String description, int gridX, int gridY, String iconType) {}
    /** Represents a directed link between two nodes by their IDs. */
    public record NodeLink(int sourceId, int targetId) {}

    // --- API & DATA STATE ---

    /** The list of all nodes currently loaded from the API. */
    private List<ProgressionNode> nodes = Collections.emptyList();
    /** The list of all links currently loaded from the API. */
    private List<NodeLink> links = Collections.emptyList();
    /** A quick-access map to find nodes by their ID. */
    private Map<Integer, ProgressionNode> nodeMap = Collections.emptyMap();
    /** The current loading state of the screen. */
    private LoadingStatus status = LoadingStatus.LOADING_GRAPH;

    // --- UI STATE ---

    private double cameraX = 0, cameraY = 0;
    private float zoom = 1.0f;
    private ProgressionNode selectedNode = null;
    private boolean isDragging = false;
    private enum LoadingStatus { IDLE, LOADING_GRAPH, LOADING_DETAILS, FAILED }
    private Set<Integer> completedMilestones = new HashSet<>();
    private @Nullable Integer currentTargetId = null;

    // --- UI ELEMENTS ---

    private Button setTargetButton;

    // --- CONSTRUCTOR & LIFECYCLE METHODS ---

    public ProgressionGraphScreen() {
        super(Component.empty());
    }

    /**
     * Called when the screen is first opened. Used to trigger the initial data load.
     */
    @Override
    protected void init() {
        super.init();

        SoundUtils.playClickSound();

        loadGraphData();
    }

    /**
     * Fetches the initial graph layout data from the API asynchronously.
     * Updates the screen's state upon completion or failure.
     */
    private void loadGraphData() {
        this.status = LoadingStatus.LOADING_GRAPH;
        String playerUuid = Minecraft.getInstance().getUser().getProfileId().toString().replace("-", "");

        ARFFORNA_API_SERVICE.fetchPlayerGraphData(playerUuid).whenComplete((playerData, error) -> {
            Minecraft.getInstance().execute(() -> {
                if (error != null) {
                    this.status = LoadingStatus.FAILED;
                    Arffornia.LOGGER.error("Failed to fetch graph data from API", error);
                    return;
                }

                if (playerData == null) {
                    this.status = LoadingStatus.FAILED;
                    Arffornia.LOGGER.error("Received null GraphData from API.");
                    return;
                }

                if (playerData.milestones() == null || playerData.milestoneClosure() == null || playerData.playerProgress() == null) {
                    this.status = LoadingStatus.FAILED;
                    Arffornia.LOGGER.error("API response is missing one or more required fields (milestones, milestone_closure, playerProgress).");
                    return;
                }

                this.nodes = playerData.milestones().stream()
                        .map(m -> new ProgressionNode(m.id(), "Loading...", "", m.x(), m.y(), m.iconType()))
                        .collect(Collectors.toList());

                this.links = playerData.milestoneClosure().stream()
                        .map(l -> new NodeLink(l.milestoneId(), l.descendantId()))
                        .collect(Collectors.toList());

                this.nodeMap = this.nodes.stream().collect(Collectors.toMap(ProgressionNode::id, node -> node));

                this.completedMilestones = new HashSet<>(playerData.playerProgress().completedMilestones());
                this.currentTargetId = playerData.playerProgress().currentTargetId();

                updateClientData();

                this.status = LoadingStatus.IDLE;
            });
        });
    }


    /**
     * The main render loop, called every frame.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (this.status == LoadingStatus.LOADING_GRAPH) {
            guiGraphics.drawCenteredString(this.font, "Loading Graph...", this.width / 2, this.height / 2, INFO_TEXT_COLOR);
            return;
        } else if (this.status == LoadingStatus.FAILED) {
            guiGraphics.drawCenteredString(this.font, "Failed to load data. See logs for details.", this.width / 2, this.height / 2, 0xFFCC0000);
            return;
        }

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
        guiGraphics.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);
        this.drawGrid(guiGraphics);
    }

    // --- DRAWING METHODS ---

    /**
     * Draws the two-layer grid with crosses at intersections.
     */
    private void drawGrid(GuiGraphics guiGraphics) {
        final int mainGridCellSize = BASE_GRID_CELL_SPACING;
        final int subGridCellSize = mainGridCellSize / 4;
        final int crossSize = 5;

        double worldLeft = this.cameraX / this.zoom;
        double worldTop = this.cameraY / this.zoom;
        double worldRight = (this.cameraX + this.width) / this.zoom;
        double worldBottom = (this.cameraY + this.height) / this.zoom;

        drawGridLines(guiGraphics, subGridCellSize, worldLeft, worldTop, worldRight, worldBottom, GRID_SUB_LINE_COLOR);
        drawGridLines(guiGraphics, mainGridCellSize, worldLeft, worldTop, worldRight, worldBottom, GRID_MAIN_LINE_COLOR);

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

    private void drawGridLines(GuiGraphics guiGraphics, int cellSize, double worldLeft, double worldTop, double worldRight, double worldBottom, int color) {
        float firstVerticalLine = (float) (Math.floor(worldLeft / cellSize) * cellSize);
        for (float x = firstVerticalLine; x < worldRight; x += cellSize) {
            int screenX = (int) (x * this.zoom - this.cameraX);
            guiGraphics.fill(screenX, 0, screenX + 1, this.height, color);
        }

        float firstHorizontalLine = (float) (Math.floor(worldTop / cellSize) * cellSize);
        for (float y = firstHorizontalLine; y < worldBottom; y += cellSize) {
            int screenY = (int) (y * this.zoom - this.cameraY);
            guiGraphics.fill(0, screenY, this.width, screenY + 1, color);
        }
    }

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

    private void drawNodes(GuiGraphics guiGraphics) {
        for (ProgressionNode node : nodes) {
            Vector2i nodePos = getScreenPosForNode(node);
            int nodeDiameter = (int)(BASE_NODE_DIAMETER * this.zoom);
            int iconDiameter = (int)(BASE_ICON_DIAMETER * this.zoom);

            if (nodePos.x + nodeDiameter/2 < 0 || nodePos.x - nodeDiameter/2 > this.width ||
                    nodePos.y + nodeDiameter/2 < 0 || nodePos.y - nodeDiameter/2 > this.height) {
                continue;
            }

            int nodeScreenX = nodePos.x - nodeDiameter / 2;
            int nodeScreenY = nodePos.y - nodeDiameter / 2;
            guiGraphics.blit(NODE_BACKGROUND_TEXTURE, nodeScreenX, nodeScreenY, 0, 0, nodeDiameter, nodeDiameter, nodeDiameter, nodeDiameter);

            ResourceLocation iconTexture = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/icons/" + node.iconType() + ".png");
            guiGraphics.blit(iconTexture, nodeScreenX + (nodeDiameter - iconDiameter) / 2, nodeScreenY + (nodeDiameter - iconDiameter) / 2, 0, 0, iconDiameter, iconDiameter, iconDiameter, iconDiameter);
        }
    }

    private void drawElbowConnection(GuiGraphics guiGraphics, ProgressionNode source, ProgressionNode target) {
        Vector2i start = getScreenPosForNode(source);
        Vector2i end = getScreenPosForNode(target);
        int lineThickness = (int)Math.max(1, BASE_LINE_THICKNESS * this.zoom);

        int midX = start.x + (end.x - start.x) / 2;
        Vector2i elbow1 = new Vector2i(midX, start.y);
        Vector2i elbow2 = new Vector2i(midX, end.y);

        drawThickLine(guiGraphics, start, elbow1, lineThickness, LINE_COLOR);
        drawThickLine(guiGraphics, elbow1, elbow2, lineThickness, LINE_COLOR);
        drawThickLine(guiGraphics, elbow2, end, lineThickness, LINE_COLOR);
    }

    private void drawStraightLine(GuiGraphics guiGraphics, ProgressionNode source, ProgressionNode target) {
        Vector2i start = getScreenPosForNode(source);
        Vector2i end = getScreenPosForNode(target);
        int lineThickness = (int)Math.max(1, BASE_LINE_THICKNESS * this.zoom);
        drawThickLine(guiGraphics, start, end, lineThickness, LINE_COLOR);
    }

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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (hasControlDown()) {
            double mouseWorldXBefore = (mouseX + this.cameraX) / this.zoom;
            double mouseWorldYBefore = (mouseY + this.cameraY) / this.zoom;
            float zoomFactor = (verticalAmount > 0) ? 1.1f : (1.0f / 1.1f);
            this.zoom = Mth.clamp(this.zoom * zoomFactor, 0.15f, 2.5f);
            this.cameraX = mouseWorldXBefore * this.zoom - mouseX;
            this.cameraY = mouseWorldYBefore * this.zoom - mouseY;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0) {
            boolean aNodeWasClicked = false;

            double nodeCheckRadius = (BASE_NODE_DIAMETER / 2.0);

            double mouseWorldX = (mouseX + this.cameraX) / this.zoom;
            double mouseWorldY = (mouseY + this.cameraY) / this.zoom;

            for (ProgressionNode node : nodes) {
                double nodeWorldX = node.gridX() * BASE_GRID_CELL_SPACING;
                double nodeWorldY = node.gridY() * BASE_GRID_CELL_SPACING;

                double dist = Math.sqrt(Math.pow(nodeWorldX - mouseWorldX, 2) + Math.pow(nodeWorldY - mouseWorldY, 2));

                if (dist <= nodeCheckRadius) {
                    this.selectedNode = node;
                    this.isDragging = false;
                    aNodeWasClicked = true;

                    fetchAndApplyNodeDetails(node.id());
                    break;
                }
            }

            if (!aNodeWasClicked) {
                this.selectedNode = null;
                this.isDragging = true;
            }

            updateDetailsPanel();

            return true;
        }

        return false;
    }

    private void fetchAndApplyNodeDetails(int nodeId) {
        this.status = LoadingStatus.LOADING_DETAILS;
        ARFFORNA_API_SERVICE.fetchMilestoneDetails(nodeId).whenComplete((details, error) -> {
            Minecraft.getInstance().execute(() -> {
                if (error != null) {
                    this.status = LoadingStatus.FAILED;
                    error.printStackTrace();
                    return;
                }

                this.nodes = this.nodes.stream()
                        .map(n -> (n.id() == nodeId) ? new ProgressionNode(n.id(), details.name(), details.description(), n.gridX(), n.gridY(), n.iconType()) : n)
                        .collect(Collectors.toList());

                this.nodeMap = this.nodes.stream().collect(Collectors.toMap(ProgressionNode::id, node -> node));

                if (this.selectedNode != null && this.selectedNode.id() == nodeId) {
                    this.selectedNode = this.nodeMap.get(nodeId);
                }

                this.status = LoadingStatus.IDLE;
            });
        });
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDragging && button == 0) {
            this.cameraX -= dragX;
            this.cameraY -= dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

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

    private void updateDetailsPanel() {
        this.clearWidgets();

        if (this.selectedNode != null) {
            this.setTargetButton = Button.builder(
                            Component.literal("Set as Target"),
                            (button) -> this.handleSetTarget()
                    )
                    .bounds(this.width - 150, this.height - 40, 130, 20)
                    .build();

            updateTargetButtonState();

            this.addRenderableWidget(this.setTargetButton);
        }
    }

    private void updateTargetButtonState() {
        if (this.setTargetButton == null || this.selectedNode == null) return;

        if (this.currentTargetId != null && this.currentTargetId.equals(this.selectedNode.id())) {
            this.setTargetButton.setMessage(Component.literal("✔ Targeted"));
            this.setTargetButton.active = false;
        } else {
            this.setTargetButton.setMessage(Component.literal("Set as Target"));
            this.setTargetButton.active = true;
        }
    }

    private void handleSetTarget() {
        if (this.selectedNode == null) return;

        int newTargetId = this.selectedNode.id();

        // TODO: Get the real player auth token
        String fakeAuthToken = "minecraft-server-svc";

        String playerUuid = Minecraft.getInstance().getUser().getProfileId().toString().replace("-", "");

        ARFFORNA_API_SERVICE.setTargetMilestone(newTargetId, fakeAuthToken, playerUuid).thenAccept(success -> {
            if (success) {
                this.currentTargetId = newTargetId;
                updateClientData();
                Minecraft.getInstance().execute(this::updateTargetButtonState);
            }
        });
    }

    private void updateClientData() {
        if (this.currentTargetId != null && this.nodeMap.containsKey(this.currentTargetId)) {
            ClientProgressionData.currentMilestoneTarget = this.nodeMap.get(this.currentTargetId).name();
        } else {
            ClientProgressionData.currentMilestoneTarget = "None";
        }
    }
}