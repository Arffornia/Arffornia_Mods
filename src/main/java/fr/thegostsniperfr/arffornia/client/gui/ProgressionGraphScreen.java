package fr.thegostsniperfr.arffornia.client.gui;


import com.mojang.math.Axis;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.client.ClientProgressionData;
import fr.thegostsniperfr.arffornia.client.util.SoundUtils;
import fr.thegostsniperfr.arffornia.network.ServerboundSetTargetMilestonePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
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

    private static final ResourceLocation TEX_NODE_COMPLETED = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/node_background.png");
    private static final ResourceLocation TEX_NODE_AVAILABLE = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/node_background.png");
    private static final ResourceLocation TEX_NODE_LOCKED = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/node_background.png");
    private static final ResourceLocation TEX_NODE_TARGET = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/node_background.png");

    private static final int COLOR_LINK_COMPLETED = 0xFF4CAF50;
    private static final int COLOR_LINK_AVAILABLE = 0xFFFF9800;
    private static final int COLOR_LINK_LOCKED = 0xFF9E9E9E;

    private static final float ROTATION_SPEED = 2.5f;

    // --- DATA STRUCTURES ---

    /**
     * Represents a single, fully-detailed node in the progression graph.
     */
    public record ProgressionNode(int id, String name, String description, int gridX, int gridY, String iconType, int stageNumber) {
    }

    /**
     * Represents a directed link between two nodes by their IDs.
     */
    public record NodeLink(int sourceId, int targetId) {
    }

    // --- API & DATA STATE ---

    /**
     * The list of all nodes currently loaded from the API.
     */
    private List<ProgressionNode> nodes = Collections.emptyList();
    /**
     * The list of all links currently loaded from the API.
     */
    private List<NodeLink> links = Collections.emptyList();
    /**
     * A quick-access map to find nodes by their ID.
     */
    private Map<Integer, ProgressionNode> nodeMap = Collections.emptyMap();
    /**
     * The current loading state of the screen.
     */
    private LoadingStatus status = LoadingStatus.LOADING_GRAPH;
    /**
     * Current milestone selected details info
     **/
    private @Nullable ArfforniaApiDtos.MilestoneDetails selectedNodeDetails = null;

    // --- UI STATE ---

    private double cameraX = 0, cameraY = 0;
    private float zoom = 1.0f;
    private ProgressionNode selectedNode = null;
    private boolean isDragging = false;

    private enum LoadingStatus {IDLE, LOADING_GRAPH, LOADING_DETAILS, FAILED}

    private Set<Integer> completedMilestones = new HashSet<>();
    private @Nullable Integer currentTargetId = null;
    private Set<Integer> availableMilestones = new HashSet<>();
    private float rotationAngle = 0.0f;

    private int maxStageNumber = 1;

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
                        .map(m -> new ProgressionNode(m.id(), "Loading...", "", m.x(), m.y(), m.iconType(),  m.stageNumber()))
                        .collect(Collectors.toList());

                this.links = playerData.milestoneClosure().stream()
                        .map(l -> new NodeLink(l.milestoneId(), l.descendantId()))
                        .collect(Collectors.toList());

                this.nodeMap = this.nodes.stream().collect(Collectors.toMap(ProgressionNode::id, node -> node));

                this.completedMilestones = new HashSet<>(playerData.playerProgress().completedMilestones());
                this.currentTargetId = playerData.playerProgress().currentTargetId();
                this.maxStageNumber = playerData.playerProgress().maxStageNumber();

                calculateAvailableMilestones();

                this.status = LoadingStatus.IDLE;

                if (this.currentTargetId != null) {
                    fetchAndApplyNodeDetails(this.currentTargetId);
                } else {
                    updateClientData();
                }
            });
        });
    }

    /**
     * Calculates which milestones are available but not yet completed.
     * A milestone is available if at least one of its direct parents is completed.
     */
    private void calculateAvailableMilestones() {
        this.availableMilestones.clear();
        for (NodeLink link : this.links) {
            // If the parent is completed but the child is not, the child becomes available.
            if (this.completedMilestones.contains(link.sourceId()) && !this.completedMilestones.contains(link.targetId())) {
                this.availableMilestones.add(link.targetId());
            }
        }
    }


    /**
     * The main render loop, called every frame.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.rotationAngle += partialTick * ROTATION_SPEED;
        if (this.rotationAngle >= 360.0f) {
            this.rotationAngle -= 360.0f;
        }


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
                int scaledCrossSize = (int) (crossSize * this.zoom);
                if (scaledCrossSize < 3) continue;

                guiGraphics.fill(crossScreenX - scaledCrossSize / 2, crossScreenY, crossScreenX + scaledCrossSize / 2 + 1, crossScreenY + 1, GRID_CROSS_COLOR);
                guiGraphics.fill(crossScreenX, crossScreenY - scaledCrossSize / 2, crossScreenX + 1, crossScreenY + scaledCrossSize / 2 + 1, GRID_CROSS_COLOR);
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
        List<NodeLink> lockedLinks = new ArrayList<>();
        List<NodeLink> availableLinks = new ArrayList<>();
        List<NodeLink> completedLinks = new ArrayList<>();

        for (NodeLink link : this.links) {
            ProgressionNode source = nodeMap.get(link.sourceId());
            ProgressionNode target = nodeMap.get(link.targetId());
            if (source == null || target == null) continue;

            if (completedMilestones.contains(source.id()) && completedMilestones.contains(target.id())) {
                completedLinks.add(link);
            } else if (completedMilestones.contains(source.id()) && !completedMilestones.contains(target.id())) {
                availableLinks.add(link);
            } else {
                lockedLinks.add(link);
            }
        }

        drawLinkList(guiGraphics, lockedLinks, COLOR_LINK_LOCKED);
        drawLinkList(guiGraphics, availableLinks, COLOR_LINK_AVAILABLE);
        drawLinkList(guiGraphics, completedLinks, COLOR_LINK_COMPLETED);
    }

    private void drawLinkList(GuiGraphics guiGraphics, List<NodeLink> linkList, int color) {
        for (NodeLink link : linkList) {
            ProgressionNode source = nodeMap.get(link.sourceId());
            ProgressionNode target = nodeMap.get(link.targetId());
            if (source == null || target == null) continue;

            if (source.gridX() == target.gridX() || source.gridY() == target.gridY()) {
                drawStraightLine(guiGraphics, source, target, color);
            } else {
                drawElbowConnection(guiGraphics, source, target, color);
            }
        }
    }

    private void drawNodes(GuiGraphics guiGraphics) {
        for (ProgressionNode node : nodes) {
            Vector2i nodePos = getScreenPosForNode(node);
            int nodeDiameter = (int) (BASE_NODE_DIAMETER * this.zoom);
            int iconDiameter = (int) (BASE_ICON_DIAMETER * this.zoom);

            if (nodePos.x + nodeDiameter / 2 < 0 || nodePos.x - nodeDiameter / 2 > this.width ||
                    nodePos.y + nodeDiameter / 2 < 0 || nodePos.y - nodeDiameter / 2 > this.height) {
                continue;
            }

            int nodeScreenX = nodePos.x - nodeDiameter / 2;
            int nodeScreenY = nodePos.y - nodeDiameter / 2;

            ResourceLocation iconTexture = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/icons/" + node.iconType() + ".png");
            ResourceLocation backgroundTexture;
            boolean shouldAnimate = false;

            if (currentTargetId != null && currentTargetId.equals(node.id())) {
                backgroundTexture = TEX_NODE_TARGET;
                shouldAnimate = true;
            } else if (completedMilestones.contains(node.id())) {
                backgroundTexture = TEX_NODE_COMPLETED;
            } else if (availableMilestones.contains(node.id())) {
                backgroundTexture = TEX_NODE_AVAILABLE;
            } else {
                backgroundTexture = TEX_NODE_LOCKED;
            }

            if (shouldAnimate) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(nodePos.x(), nodePos.y(), 0);
                guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(this.rotationAngle));
                guiGraphics.pose().translate(-nodeDiameter / 2.0, -nodeDiameter / 2.0, 0);

                guiGraphics.blit(backgroundTexture, 0, 0, 0, 0, nodeDiameter, nodeDiameter, nodeDiameter, nodeDiameter);
                guiGraphics.blit(iconTexture, (nodeDiameter - iconDiameter) / 2, (nodeDiameter - iconDiameter) / 2, 0, 0, iconDiameter, iconDiameter, iconDiameter, iconDiameter);

                guiGraphics.pose().popPose();
            } else {
                guiGraphics.blit(backgroundTexture, nodeScreenX, nodeScreenY, 0, 0, nodeDiameter, nodeDiameter, nodeDiameter, nodeDiameter);
                guiGraphics.blit(iconTexture, nodeScreenX + (nodeDiameter - iconDiameter) / 2, nodeScreenY + (nodeDiameter - iconDiameter) / 2, 0, 0, iconDiameter, iconDiameter, iconDiameter, iconDiameter);
            }
        }
    }

    private void drawElbowConnection(GuiGraphics guiGraphics, ProgressionNode source, ProgressionNode target, int color) {
        Vector2i start = getScreenPosForNode(source);
        Vector2i end = getScreenPosForNode(target);
        int lineThickness = (int) Math.max(1, BASE_LINE_THICKNESS * this.zoom);

        int midX = start.x + (end.x - start.x) / 2;
        Vector2i elbow1 = new Vector2i(midX, start.y);
        Vector2i elbow2 = new Vector2i(midX, end.y);

        drawThickLine(guiGraphics, start, elbow1, lineThickness, color);
        drawThickLine(guiGraphics, elbow1, elbow2, lineThickness, color);
        drawThickLine(guiGraphics, elbow2, end, lineThickness, color);
    }

    private void drawStraightLine(GuiGraphics guiGraphics, ProgressionNode source, ProgressionNode target, int color) {
        Vector2i start = getScreenPosForNode(source);
        Vector2i end = getScreenPosForNode(target);
        int lineThickness = (int) Math.max(1, BASE_LINE_THICKNESS * this.zoom);
        drawThickLine(guiGraphics, start, end, lineThickness, color);
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
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    private void drawInfoPanel(GuiGraphics guiGraphics, ProgressionNode node) {
        int panelWidth = 170;
        int panelX = this.width - panelWidth - 20;
        int panelY = 20;
        int padding = 8;
        int textColor = 0xFF_D0D0D0;
        int titleColor = 0xFF_FFFFFF;
        int headerColor = 0xFF_FFAA00;

        guiGraphics.fill(panelX, panelY, panelX + panelWidth, this.height - 20, 0xE0_1A1A1A);

        int currentY = panelY + padding;

        if (this.status == LoadingStatus.LOADING_DETAILS || this.selectedNodeDetails == null) {
            guiGraphics.drawCenteredString(this.font, "Loading details...", panelX + panelWidth / 2, panelY + 20, 0xFF_FFFFFF);
            return;
        }

        // --- Title ---
        List<FormattedCharSequence> titleLines = this.font.split(Component.literal(this.selectedNodeDetails.name()).withStyle(ChatFormatting.BOLD), panelWidth - 2 * padding);
        for (FormattedCharSequence line : titleLines) {
            guiGraphics.drawString(this.font, line, panelX + padding, currentY, titleColor);
            currentY += this.font.lineHeight;
        }
        currentY += 4;

        // --- Description ---
        List<FormattedCharSequence> descLines = this.font.split(Component.literal(this.selectedNodeDetails.description()), panelWidth - 2 * padding);
        for (FormattedCharSequence line : descLines) {
            guiGraphics.drawString(this.font, line, panelX + padding, currentY, textColor);
            currentY += this.font.lineHeight;
        }
        currentY += 8;

        // --- Basic Info (Stage & Points) ---
        guiGraphics.drawString(this.font, Component.literal("Id: ").withStyle(ChatFormatting.GRAY).append(Component.literal("" + this.selectedNodeDetails.id()).withStyle(ChatFormatting.YELLOW)), panelX + padding, currentY, 0xFF_FFFFFF);
        currentY += this.font.lineHeight;
        guiGraphics.drawString(this.font, Component.literal("Stage: ").withStyle(ChatFormatting.GRAY).append(Component.literal("" + this.selectedNodeDetails.stageId()).withStyle(ChatFormatting.YELLOW)), panelX + padding, currentY, 0xFF_FFFFFF);
        currentY += this.font.lineHeight;
        guiGraphics.drawString(this.font, Component.literal("Points: ").withStyle(ChatFormatting.GRAY).append(Component.literal("" + this.selectedNodeDetails.rewardProgressPoints()).withStyle(ChatFormatting.AQUA)), panelX + padding, currentY, 0xFF_FFFFFF);
        currentY += 10;

        // --- Unlocked Items Section ---
        if (!this.selectedNodeDetails.unlocks().isEmpty()) {
            guiGraphics.drawString(this.font, Component.literal("New Items:").setStyle(Style.EMPTY.withColor(headerColor)), panelX + padding, currentY, headerColor);
            currentY += this.font.lineHeight + 2;

            for (ArfforniaApiDtos.MilestoneUnlock unlock : this.selectedNodeDetails.unlocks()) {
                ItemStack itemStack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(unlock.itemId())));
                guiGraphics.renderItem(itemStack, panelX + padding, currentY);
                String price = unlock.shopPrice() != null ? " (Price: " + unlock.shopPrice() + ")" : "";
                guiGraphics.drawString(this.font, unlock.displayName() + price, panelX + padding + 20, currentY + 4, textColor);
                currentY += 18;
            }
            currentY += 8;
        }

        // --- Required Items Section ---
        if (!this.selectedNodeDetails.requirements().isEmpty()) {
            guiGraphics.drawString(this.font, Component.literal("Required Items:").setStyle(Style.EMPTY.withColor(headerColor)), panelX + padding, currentY, headerColor);
            currentY += this.font.lineHeight + 2;

            for (ArfforniaApiDtos.MilestoneRequirement req : this.selectedNodeDetails.requirements()) {
                ItemStack itemStack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(req.itemId())), req.amount());
                guiGraphics.renderItem(itemStack, panelX + padding, currentY);
                guiGraphics.renderItemDecorations(this.font, itemStack, panelX + padding, currentY);
                guiGraphics.drawString(this.font, req.displayName(), panelX + padding + 20, currentY + 4, textColor);
                currentY += 18;
            }
        }
    }

    // --- INTERACTION HANDLERS ---

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double mouseWorldXBefore = (mouseX + this.cameraX) / this.zoom;
        double mouseWorldYBefore = (mouseY + this.cameraY) / this.zoom;
        float zoomFactor = (verticalAmount > 0) ? 1.1f : (1.0f / 1.1f);
        this.zoom = Mth.clamp(this.zoom * zoomFactor, 0.15f, 2.5f);
        this.cameraX = mouseWorldXBefore * this.zoom - mouseX;
        this.cameraY = mouseWorldYBefore * this.zoom - mouseY;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0) {
            if (this.selectedNode != null) {
                int panelWidth = 170;
                int panelX = this.width - panelWidth - 20;
                int panelY = 20;
                int panelBottom = this.height - 20;

                if (mouseX >= panelX && mouseX <= (panelX + panelWidth) && mouseY >= panelY && mouseY <= panelBottom) {
                    return true;
                }
            }


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
        updateDetailsPanel();

        ARFFORNA_API_SERVICE.fetchMilestoneDetails(nodeId).whenComplete((details, error) -> {
            Minecraft.getInstance().execute(() -> {
                if (error != null) {
                    this.status = LoadingStatus.FAILED;
                    error.printStackTrace();
                    return;
                }

                this.selectedNodeDetails = details;

                this.nodes = this.nodes.stream()
                        .map(n -> (n.id() == nodeId) ? new ProgressionNode(n.id(), details.name(), details.description(), n.gridX(), n.gridY(), n.iconType(), n.stageNumber()) : n)
                        .collect(Collectors.toList());

                this.nodeMap = this.nodes.stream().collect(Collectors.toMap(ProgressionNode::id, node -> node));

                if (this.selectedNode != null && this.selectedNode.id() == nodeId) {
                    this.selectedNode = this.nodeMap.get(nodeId);
                }

                updateClientData();

                this.status = LoadingStatus.IDLE;

                updateTargetButtonState();
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
     *
     * @param node The node to position.
     * @return A Vector2i containing the screen-space coordinates.
     */
    private Vector2i getScreenPosForNode(ProgressionNode node) {
        return worldToScreen(node.gridX() * BASE_GRID_CELL_SPACING, node.gridY() * BASE_GRID_CELL_SPACING);
    }

    /**
     * Converts world coordinates to screen coordinates based on camera and zoom.
     *
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

        if (completedMilestones.contains(this.selectedNode.id())) {
            this.setTargetButton.setMessage(Component.literal("✔ Completed"));
            this.setTargetButton.active = false;
            return;
        }

        if (this.currentTargetId != null && this.currentTargetId.equals(this.selectedNode.id())) {
            this.setTargetButton.setMessage(Component.literal("✔ Targeted"));
            this.setTargetButton.active = false;
            return;
        }

        if (this.selectedNodeDetails == null) {
            this.setTargetButton.active = false;
            return;
        }

        ProgressionNode currentNode = nodeMap.get(this.selectedNode.id());

        if (currentNode.stageNumber > this.maxStageNumber) {
            this.setTargetButton.setMessage(Component.literal("Higher Stage Locked"));
            this.setTargetButton.active = false;
            return;
        }


        List<Integer> prerequisites = this.links.stream()
                .filter(link -> link.targetId() == this.selectedNode.id())
                .map(NodeLink::sourceId)
                .toList();

        if (!prerequisites.isEmpty()) {
            boolean hasCompletedPrerequisite = prerequisites.stream()
                    .anyMatch(prereqId -> this.completedMilestones.contains(prereqId));

            if (!hasCompletedPrerequisite) {
                this.setTargetButton.setMessage(Component.literal("Prerequisite Locked"));
                this.setTargetButton.active = false;
                return;
            }
        }

        this.setTargetButton.setMessage(Component.literal("Set as Target"));
        this.setTargetButton.active = true;
    }

    private void handleSetTarget() {
        if (this.selectedNode == null) return;

        int newTargetId = this.selectedNode.id();

        PacketDistributor.sendToServer(new ServerboundSetTargetMilestonePacket(newTargetId));


        this.currentTargetId = newTargetId;

        calculateAvailableMilestones();

        updateClientData();
        updateTargetButtonState();
    }

    private void updateClientData() {
        if (this.currentTargetId != null && this.nodeMap.containsKey(this.currentTargetId)) {
            ClientProgressionData.currentMilestoneTarget = this.nodeMap.get(this.currentTargetId).name();
        } else {
            ClientProgressionData.currentMilestoneTarget = "None";
        }
    }
}